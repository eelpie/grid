import akka.Done
import akka.stream.scaladsl.Source
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration
import com.contxt.kinesis.{KinesisRecord, KinesisSource}
import com.gu.mediaservice.GridClient
import com.gu.mediaservice.lib.aws.{S3Ops, ThrallMessageSender}
import com.gu.mediaservice.lib.metadata.SoftDeletedMetadataTable
import com.gu.mediaservice.lib.play.GridComponents
import com.gu.mediaservice.model.Instance
import com.typesafe.scalalogging.StrictLogging
import controllers.{AssetsComponents, HealthCheck, ReaperController, ThrallController}
import lib._
import lib.elasticsearch._
import lib.kinesis.{KinesisConfig, ThrallEventConsumer}
import play.api.ApplicationLoader.Context
import router.Routes

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class ThrallComponents(context: Context) extends GridComponents(context, new ThrallConfig(_)) with StrictLogging with AssetsComponents {
  final override val buildInfo = utils.buildinfo.BuildInfo

  val store = new ThrallStore(config)
  val thrallMetrics = new ThrallMetrics(config)

  val es = new ElasticSearch(config.esConfig, Some(thrallMetrics), actorSystem.scheduler)
  // es.ensureIndexExistsAndAliasAssigned() TODO needs to move somewhere else

  val gridClient: GridClient = GridClient(config.services)(wsClient)

  // before firing up anything to consume streams or say we are OK let's do the critical good to go check
  // TODO restore a reduced non instance specific version of this private val goodToGoCheckResult = Await.ready(GoodToGoCheck.run(es), 30 seconds)

  val messageSender = new ThrallMessageSender(config.thrallKinesisStreamConfig)

  val highPriorityKinesisConfig: KinesisClientLibConfiguration = KinesisConfig.kinesisConfig(config.kinesisConfig)
  val lowPriorityKinesisConfig: KinesisClientLibConfiguration = KinesisConfig.kinesisConfig(config.kinesisLowPriorityConfig)

  val uiSource: Source[KinesisRecord, Future[Done]] = KinesisSource(highPriorityKinesisConfig)
  val automationSource: Source[KinesisRecord, Future[Done]] = KinesisSource(lowPriorityKinesisConfig)
  val migrationSourceWithSender: MigrationSourceWithSender = MigrationSourceWithSender(materializer, auth.innerServiceCall, es, gridClient, config.projectionParallelism, Instance("an-instance"))  // TODO move to a more multi instance aware place

  val thrallEventConsumer = new ThrallEventConsumer(
    es,
    thrallMetrics,
    store,
    actorSystem
  )

  val thrallStreamProcessor = new ThrallStreamProcessor(
    uiSource,
    automationSource,
    migrationSourceWithSender.source,
    thrallEventConsumer,
    actorSystem
  )

  val streamRunning: Future[Done] = thrallStreamProcessor.run()

  val s3 = S3Ops.buildS3Client(config)

  val softDeletedMetadataTable = new SoftDeletedMetadataTable(config)
  val maybeCustomReapableEligibility = config.maybeReapableEligibilityClass(applicationLifecycle)

  val thrallController = new ThrallController(es, store, migrationSourceWithSender.send, messageSender, actorSystem, auth, config.services, controllerComponents, gridClient)
  val reaperController = new ReaperController(es, store, authorisation, config, actorSystem.scheduler, maybeCustomReapableEligibility, softDeletedMetadataTable, thrallMetrics, auth, config.services, controllerComponents)
  val healthCheckController = new HealthCheck(es, streamRunning.isCompleted, config, controllerComponents)

  override lazy val router = new Routes(httpErrorHandler, thrallController, reaperController, healthCheckController, management, assets)
}

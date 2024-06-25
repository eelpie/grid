import akka.Done
import akka.stream.scaladsl.Source
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration
import com.contxt.kinesis.{KinesisRecord, KinesisSource}
import com.gu.mediaservice.GridClient
import com.gu.mediaservice.lib.aws.{S3Ops, ThrallMessageSender}
import com.gu.mediaservice.lib.logging.MarkerMap
import com.gu.mediaservice.lib.metadata.SoftDeletedMetadataTable
import com.gu.mediaservice.lib.play.GridComponents
import com.gu.mediaservice.model.Instance
import com.typesafe.scalalogging.StrictLogging
import controllers.{AssetsComponents, HealthCheck, ReaperController, ThrallController}
import lib._
import lib.elasticsearch._
import lib.kinesis.{KinesisConfig, ThrallEventConsumer}
import play.api.ApplicationLoader.Context
import play.api.libs.json.Json
import play.api.libs.ws.WSRequest
import router.Routes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{GetQueueUrlRequest, SendMessageRequest}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

class ThrallComponents(context: Context) extends GridComponents(context, new ThrallConfig(_)) with StrictLogging with AssetsComponents {
  final override val buildInfo = utils.buildinfo.BuildInfo

  val store = new ThrallStore(config)
  val thrallMetrics = new ThrallMetrics(config)

  val es = new ElasticSearch(config.esConfig, Some(thrallMetrics), actorSystem.scheduler)

  val gridClient: GridClient = GridClient(config.services)(wsClient)

  private val sqsClient = SqsClient.builder()
    .region(Region.EU_WEST_1)
    .build()

  private val queueUrl = {
    val getQueueRequest = GetQueueUrlRequest.builder()
      .queueName(config.instanceUsageQueueName)
      .build();
    sqsClient.getQueueUrl(getQueueRequest).queueUrl
  }

  // before firing up anything to consume streams or say we are OK let's do the critical good to go check
  def ensureIndexes(): Future[Unit] = {
    logger.info("Ensuring indexes")
    getInstances.map { instances =>
      instances.foreach{ instance =>
        logger.info("Ensuing index for: " + instance.id)
        es.ensureIndexExistsAndAliasAssigned(alias = es.imagesCurrentAlias(instance), index = instance.id + "_index")
      }
    }
  }
  Await.ready(ensureIndexes(), 60 seconds)

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
    actorSystem,
    gridClient,
    auth
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

  Source.repeat(()).throttle(1, per = 5.minute).map(_ => {
    implicit val logMarker: MarkerMap = MarkerMap()
    getInstances.map { instances =>
      // Foreach instance; query elastic for number image and total file size
      instances.foreach { instance =>
        logger.info("Checking usage for: " + instance)
        implicit val i = instance
        val eventualLong = es.countTotal()
        val eventualTotalSize = es.countTotalSize()
        for {
          count <- eventualLong
          totalSize <- eventualTotalSize
        } yield {
          logger.info("Instance " + instance.id + " has " + count + " images / total size: " + totalSize)
          val message = InstanceUsageMessage(instance = instance.id, imageCount = count, totalImageSize = totalSize)
          implicit val iumw = Json.writes[InstanceUsageMessage]
          sqsClient.sendMessage(SendMessageRequest.builder.queueUrl(queueUrl).messageBody(Json.toJson(message).toString()).build)
        }
      }
    }
    // TODO Block?

  }).run()


  val softDeletedMetadataTable = new SoftDeletedMetadataTable(config)
  val maybeCustomReapableEligibility = config.maybeReapableEligibilityClass(applicationLifecycle)

  val thrallController = new ThrallController(es, store, migrationSourceWithSender.send, messageSender, actorSystem, auth, config.services, controllerComponents, gridClient)
  val reaperController = new ReaperController(es, store, authorisation, config, actorSystem.scheduler, maybeCustomReapableEligibility, softDeletedMetadataTable, thrallMetrics, auth, config.services, controllerComponents)
  val healthCheckController = new HealthCheck(es, streamRunning.isCompleted, config, controllerComponents)

  override lazy val router = new Routes(httpErrorHandler, thrallController, reaperController, healthCheckController, management, assets)

  private def getInstances: Future[Seq[Instance]] = {
    val instancesRequest: WSRequest = wsClient.url(config.instancesEndpoint)
    val eventualAllInstances = instancesRequest.get().map { r =>
      r.status match {
        case 200 =>
          implicit val ir = Json.reads[Instance]
          val instances = Json.parse(r.body).as[Seq[Instance]]
          logger.info("Got instances: " + instances.map(_.id).mkString(","))
          instances
        case _ =>
          logger.warn("Got non 200 status for instances call: " + r.status)
          Seq.empty
      }
    }
    eventualAllInstances
  }
}

case class InstanceUsageMessage(instance: String, imageCount: Long, totalImageSize: Double)

import app.photofox.vipsffm.{Vips, VipsHelper}
import com.gu.mediaservice.GridClient
import com.gu.mediaservice.lib.aws.{Embedder, S3, SimpleSqsMessageConsumer}
import com.gu.mediaservice.lib.embeddings.GoogleCloudEmbedding
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.logging.GridLogging
import com.gu.mediaservice.lib.play.GridComponents
import controllers.{ImageLoaderController, ImageLoaderManagement, UploadStatusController}
import lib._
import lib.storage.{ImageLoaderStore, QuarantineStore}
import model.upload.OptimiseWithPngQuant
import model.{Projector, QuarantineUploader, Uploader}
import play.api.ApplicationLoader.Context
import router.Routes

class ImageLoaderComponents(context: Context) extends GridComponents(context, new ImageLoaderConfig(_)) with GridLogging {
  final override val buildInfo = utils.buildinfo.BuildInfo

  private val imageProcessor = config.imageProcessor(applicationLifecycle)
  logger.info(s"Loaded ${imageProcessor.processors.size} image processors:")
  imageProcessor.processors.zipWithIndex.foreach { case (processor, index) =>
    logger.info(s" $index -> ${processor.description}")
  }

  private val gridClient = GridClient(config.services, config.services.loaderBaseUri)(wsClient)

  val store = new ImageLoaderStore(config)
  val maybeIngestQueue = config.maybeIngestSqsQueueUrl.map(queueUrl => new SimpleSqsMessageConsumer(queueUrl, config))
  val uploadStatusTable = new UploadStatusTable(config)
  val imageOperations = {
    Vips.init()
    VipsHelper.cache_set_max(0)
    new ImageOperations(context.environment.rootPath.getAbsolutePath)
  }
  val notifications = new Notifications(config)
  val downloader = new Downloader()(ec,wsClient)

  private val maybeGcpProjectId = config.gcpProjectId
  private val vertexApiLocation = "eu"
  private val maybeGoogleCloudEmbedding = for {
    gcpProjectId <- maybeGcpProjectId
  } yield {
    new GoogleCloudEmbedding(projectId = gcpProjectId, location = vertexApiLocation)
  }

  private val maybeEmbedding = maybeGoogleCloudEmbedding

  val maybeEmbedder: Option[Embedder] = for {
    embedding <- maybeEmbedding
    queueUrl <- config.maybeImageEmbedderQueueUrl.filter(_ => config.shouldEmbed)
  } yield {

    logger.info("Image loader is configured to queue embedding requests to: " + queueUrl)
      new Embedder(embedding, new SimpleSqsMessageConsumer(queueUrl, config))
  }

  private val s3 = new S3(config)

  val optimiseOps = new OptimiseWithPngQuant(imageOperations)
  val uploader = new Uploader(store, config, imageOperations, notifications, maybeEmbedder, imageProcessor, gridClient, auth, optimiseOps)
  val projector = Projector(config, s3, imageOperations, imageProcessor, auth, maybeEmbedder, optimiseOps)
  val quarantineUploader: Option[QuarantineUploader] = config.maybeQuarantineBucket.map(_ =>
    new QuarantineUploader(new QuarantineStore(config), config)
  )

  val metrics = new ImageLoaderMetrics(config, actorSystem, applicationLifecycle)

  val controller = new ImageLoaderController(
    auth, downloader, store, maybeIngestQueue, uploadStatusTable, config, uploader, quarantineUploader, projector, controllerComponents, gridClient, authorisation, metrics, usageEvents, wsClient, applicationLifecycle)
  val uploadStatusController = new UploadStatusController(auth, uploadStatusTable, config, controllerComponents, authorisation)
  val imageLoaderManagement = new ImageLoaderManagement(controllerComponents, buildInfo, controller.maybeIngestQueueAndProcessor)

  override lazy val router = new Routes(httpErrorHandler, controller, uploadStatusController, imageLoaderManagement)
}

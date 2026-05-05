import app.photofox.vipsffm.{Vips, VipsHelper}
import com.gu.mediaservice.GridClient
import com.gu.mediaservice.lib.aws._
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

  private val gridClient = GridClient(config.services)(wsClient)

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

  val maybeEmbedder: Option[Embedder] = config.maybeImageEmbedderQueueUrl
    .filter(_ => config.shouldEmbed)
    .map {queueUrl =>
      new Embedder(
        new S3Vectors(config),
        new Bedrock(config),
        new SimpleSqsMessageConsumer(queueUrl, config)
      )
    }

  val optimiseOps = new OptimiseWithPngQuant(imageOperations)
  val uploader = new Uploader(store, config, imageOperations, notifications, maybeEmbedder, imageProcessor, gridClient, auth, optimiseOps)
  val s3 = new S3(config)
  val projector = Projector(config, imageOperations, imageProcessor, auth, maybeEmbedder, s3, optimiseOps)
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

package model

import _root_.play.api.libs.json.Json
import _root_.play.api.libs.ws.WSRequest
import com.gu.mediaservice.lib.Files.createTempFile
import com.gu.mediaservice.lib._
import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.aws._
import com.gu.mediaservice.lib.cleanup.ImageProcessor
import com.gu.mediaservice.lib.formatting._
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.imaging.ImageOperations.{optimisedMimeType, thumbMimeType}
import com.gu.mediaservice.lib.logging._
import com.gu.mediaservice.lib.metadata.ImageMetadataConverter
import com.gu.mediaservice.lib.net.URI
import com.gu.mediaservice.model._
import com.gu.mediaservice.syntax.MessageSubjects
import com.gu.mediaservice.{GridClient, ImageDataMerger}
import lib.imaging.{FileMetadataReader, MimeTypeDetection}
import lib.storage.ImageLoaderStore
import lib.{DigestedFile, ImageLoaderConfig, Notifications}
import model.Uploader.{fromUploadRequestShared, toImageUploadOpsCfg}
import model.upload.{OptimiseOps, OptimiseWithPngQuant, UploadRequest}
import org.joda.time.DateTime

import java.io.File
import java.nio.file.Files
import scala.concurrent.{ExecutionContext, Future}

case class ImageUpload(uploadRequest: UploadRequest, image: Image)

case object ImageUpload {

  def createImage(uploadRequest: UploadRequest, source: Asset, thumbnail: Asset, png: Option[Asset],
                  fileMetadata: FileMetadata, metadata: ImageMetadata): Image = {
    val usageRights = NoRights
    Image(
      uploadRequest.imageId,
      uploadRequest.uploadTime,
      uploadRequest.uploadedBy,
      None,
      Some(uploadRequest.uploadTime),
      uploadRequest.identifiers,
      uploadRequest.uploadInfo,
      source,
      Some(thumbnail),
      png,
      fileMetadata,
      None,
      metadata,
      metadata,
      usageRights,
      usageRights,
      List(),
      List(),
      //      ImageEmbedding will be written by lambda later
      embedding = None
    )
  }
}

case class ImageUploadOpsCfg(
  tempDir: File,
  thumbWidth: Int,
  thumbQuality: Double,
  originalFileBucket: S3Bucket,
  thumbBucket: S3Bucket,
)

case class ImageUploadOpsDependencies(
  config: ImageUploadOpsCfg,
  imageOps: ImageOperations,
  storeOrProjectOriginalFile: StorableOriginalImage => Future[S3Object],
  storeOrProjectThumbFile: StorableThumbImage => Future[S3Object],
  storeOrProjectOptimisedImage: StorableOptimisedImage => Future[S3Object],
  tryFetchThumbFile: (String, File, Instance) => Future[Option[(File, MimeType)]] = (_, _, _) => Future.successful(None),
  tryFetchOptimisedFile: (String, File, Instance) => Future[Option[(File, MimeType)]] = (_, _, _) => Future.successful(None),
)


case class UploadStatusUri (uri: String) extends AnyVal {
  def toJsObject = Json.obj("uri" -> uri)
}

object Uploader extends GridLogging {

  def toImageUploadOpsCfg(config: ImageLoaderConfig): ImageUploadOpsCfg = {
    ImageUploadOpsCfg(
      config.tempDir,
      config.thumbWidth,
      config.thumbQuality,
      config.imageBucket,
      config.thumbnailBucket,
    )
  }

  def fromUploadRequestShared(uploadRequest: UploadRequest, deps: ImageUploadOpsDependencies, processor: ImageProcessor)
                             (implicit ec: ExecutionContext, logMarker: LogMarker): Future[Image] = {

    import deps._

    logger.info(logMarker, "Starting image ops")

    val fileMetadataFuture = toFileMetadata(uploadRequest.tempFile, uploadRequest.imageId, uploadRequest.mimeType)

    logger.info(logMarker, "Have read file headers")

    fileMetadataFuture.flatMap(fileMetadata => {
      uploadAndStoreImage(
        storeOrProjectOriginalFile,
        storeOrProjectThumbFile,
        storeOrProjectOptimisedImage,
        OptimiseWithPngQuant,
        uploadRequest,
        deps,
        processor)(ec, addLogMarkers(fileMetadata.toLogMarker))
    })
  }

  private[model] def uploadAndStoreImage(storeOrProjectOriginalFile: StorableOriginalImage => Future[S3Object],
                                         storeOrProjectThumbFile: StorableThumbImage => Future[S3Object],
                                         storeOrProjectOptimisedFile: StorableOptimisedImage => Future[S3Object],
                                         optimiseOps: OptimiseOps,
                                         uploadRequest: UploadRequest,
                                         deps: ImageUploadOpsDependencies,
                                         processor: ImageProcessor)
                  (implicit ec: ExecutionContext, logMarker: LogMarker) = {
    val originalMimeType = uploadRequest.mimeType
      .orElse(MimeTypeDetection.guessMimeType(uploadRequest.tempFile).toOption)
    match {
      case Some(a) => a
      case None => throw new Exception("File of unknown and undetectable mime type")
    }
    logger.info("Original Mime type: " + originalMimeType)

    val tempDirForRequest: File = Files.createTempDirectory(deps.config.tempDir.toPath, "upload").toFile

    val storableOriginalImage = StorableOriginalImage(
      uploadRequest.imageId,
      uploadRequest.tempFile,
      originalMimeType,
      uploadRequest.uploadTime,
      toMetaMap(uploadRequest),
      uploadRequest.instance
    )
    val sourceStoreFuture = storeOrProjectOriginalFile(storableOriginalImage)
    val eventualBrowserViewableImage = createBrowserViewableFileFuture(uploadRequest)

    val eventualImage = for {
      browserViewableImage <- eventualBrowserViewableImage
      s3Source <- sourceStoreFuture
      mergedUploadRequest = patchUploadRequestWithS3Metadata(uploadRequest, s3Source)
      imageInformation <- ImageOperations.getImageInformation(uploadRequest.tempFile)
      sourceDimensions = imageInformation._1
      sourceOrientationMetadata = imageInformation._2
      colourModel = imageInformation._3
      colourModelInformation = imageInformation._4
      fileMetadata <- toFileMetadata(uploadRequest.tempFile, uploadRequest.imageId, uploadRequest.mimeType)
      thumbViewableImage <- createThumbFuture(browserViewableImage, deps, tempDirForRequest, uploadRequest.instance, orientationMetadata = sourceOrientationMetadata)
      thumbDimensions <- ImageOperations.getImageInformation(thumbViewableImage.file).map(_._1)
      s3Thumb <- storeOrProjectThumbFile(thumbViewableImage)
      maybeStorableOptimisedImage <- getStorableOptimisedImage(
      tempDirForRequest, optimiseOps, browserViewableImage, deps.tryFetchOptimisedFile, uploadRequest.instance)
      s3PngOption <- maybeStorableOptimisedImage match {
        case Some(storableOptimisedImage) => storeOrProjectOptimisedFile(storableOptimisedImage).map(a=>Some(a))
        case None => Future.successful(None)
      }
    } yield {
      val fullFileMetadata = fileMetadata.copy(colourModel = colourModel).copy(colourModelInformation = colourModelInformation)
      val metadata = ImageMetadataConverter.fromFileMetadata(fullFileMetadata, s3Source.metadata.objectMetadata.lastModified)

      val sourceAsset = Asset.fromS3Object(s3Source, sourceDimensions, sourceOrientationMetadata)
      val thumbAsset = Asset.fromS3Object(s3Thumb, thumbDimensions)

      val pngAsset = s3PngOption.map(Asset.fromS3Object(_, sourceDimensions))
      val baseImage = ImageUpload.createImage(
        mergedUploadRequest,
        sourceAsset,
        thumbAsset,
        pngAsset,
        fullFileMetadata,
        metadata
      )
      val processedImage = processor(baseImage)

      logger.info(addLogMarkers(fileMetadata.toLogMarker), s"Ending image ops")
      // FIXME: dirty hack to sync the originalUsageRights and originalMetadata as well
      processedImage.copy(
        originalMetadata = processedImage.metadata,
        originalUsageRights = processedImage.usageRights
      )
    }
    eventualImage.onComplete{ _ =>
      tempDirForRequest.listFiles().map(f => f.delete())
      tempDirForRequest.delete()
    }
    eventualImage
  }

  private def getStorableOptimisedImage(
                                         tempDir: File,
                                         optimiseOps: OptimiseOps,
                                         browserViewableImage: BrowserViewableImage,
                                         tryFetchOptimisedFile: (String, File, Instance) => Future[Option[(File, MimeType)]],
                                         instance: Instance
  )(implicit ec: ExecutionContext, logMarker: LogMarker): Future[Option[StorableOptimisedImage]] = {
    if (optimiseOps.shouldOptimise(Some(browserViewableImage.mimeType))) {
      for {
        tempFile <- createTempFile("optimisedpng-", optimisedMimeType.fileExtension, tempDir)
        maybeDownloadedOptimisedFile <- tryFetchOptimisedFile(browserViewableImage.id, tempFile, instance)
        (optimisedFile, optimisedMimeType) <- {
          maybeDownloadedOptimisedFile match {
            case Some(optData) => Future.successful(optData)
            case None => optimiseOps.toOptimisedFile(browserViewableImage.file, browserViewableImage, tempFile)
          }
        }
      } yield Some(
        browserViewableImage.copy(
          file = optimisedFile,
          mimeType = optimisedMimeType
        ).asStorableOptimisedImage
      )
    } else if (browserViewableImage.isTransformedFromSource) {
      Future.successful(Some(browserViewableImage.asStorableOptimisedImage))
    } else
      Future.successful(None)
  }

  def toMetaMap(uploadRequest: UploadRequest): Map[String, String] = {
    val baseMeta = Map(
      ImageStorageProps.uploadedByMetadataKey -> uploadRequest.uploadedBy,
      ImageStorageProps.uploadTimeMetadataKey -> printDateTime(uploadRequest.uploadTime),
    ) ++
      uploadRequest.identifiersMeta ++
      uploadRequest.uploadInfo.filename.map(ImageStorageProps.filenameMetadataKey -> _) ++
      uploadRequest.uploadInfo.isFeedUpload.map(ImageStorageProps.isFeedUploadMetadataKey -> _.toString)

    baseMeta.view.mapValues(URI.encode).toMap
  }

  private def toFileMetadata(f: File, imageId: String, mimeType: Option[MimeType])(implicit ec: ExecutionContext, logMarker: LogMarker): Future[FileMetadata] = {
    val stopwatch = Stopwatch.start
    (mimeType match {
      //case Some(Png | Tiff | Jpeg) => FileMetadataReader.fromIPTCHeadersWithColorInfo(f, imageId, mimeType.get)
      case _ => FileMetadataReader.fromIPTCHeaders(f, imageId)
    }).map { result =>
      logger.info(addLogMarkers(stopwatch.elapsed), "Finished toFileMetadata")
      result
    }
  }

  private def createThumbFuture(browserViewableImage: BrowserViewableImage,
                                deps: ImageUploadOpsDependencies,
                                tempDir: File,
                                instance: Instance,
                                orientationMetadata: Option[OrientationMetadata]
  )(implicit ec: ExecutionContext, logMarker: LogMarker) = {
    import deps._

    def generateThumbnail(tempFile: File) = {
      for {
        thumbData <- imageOps.createThumbnailVips(
          browserViewableImage,
          config.thumbWidth,
          config.thumbQuality,
          tempFile,
          orientationMetadata,
        )
      } yield thumbData
    }

    for {
      tempFile <- createTempFile(s"thumb-", thumbMimeType.fileExtension, tempDir)
      maybeThumbFile <- deps.tryFetchThumbFile(browserViewableImage.id, tempFile, instance)
      (thumb, thumbMimeType) <- {
        maybeThumbFile match {
          case Some(thumbData) => Future.successful(thumbData)
          case None => generateThumbnail(tempFile)
        }
      }
    } yield browserViewableImage
      .copy(file = thumb, mimeType = thumbMimeType)
      .asStorableThumbImage
  }

  private def createBrowserViewableFileFuture(
    uploadRequest: UploadRequest
  )(implicit ec: ExecutionContext, logMarker: LogMarker): Future[BrowserViewableImage] = {
    uploadRequest.mimeType match {
      case Some(mimeType) =>
        Future.successful(
          BrowserViewableImage(
            uploadRequest.imageId,
            file = uploadRequest.tempFile,
            mimeType = mimeType,
            instance = uploadRequest.instance)
        )
      case None => Future.failed(new Exception("This file is not an image with an identifiable mime type"))
    }
  }

  def patchUploadRequestWithS3Metadata(request: UploadRequest, s3Object: S3Object): UploadRequest = {
    val metadata = S3FileExtractedMetadata(s3Object.metadata.objectMetadata.lastModified.getOrElse(new DateTime), s3Object.metadata.userMetadata)
    request.copy(
      uploadTime = metadata.uploadTime,
      uploadedBy = metadata.uploadedBy,
      uploadInfo = request.uploadInfo.copy(filename = metadata.uploadFileName),
      identifiers = metadata.identifiers
    )
  }
}

class Uploader(
  val store: ImageLoaderStore,
  val config: ImageLoaderConfig,
  val imageOps: ImageOperations,
  val notifications: Notifications,
  val maybeEmbedder: Option[Embedder],
               imageProcessor: ImageProcessor,
  gridClient: GridClient,
  auth: Authentication
)(
  implicit val ec: ExecutionContext
) extends MessageSubjects with ArgoHelpers {

  private def addChildUsageToParentImage(
    uploadRequest: UploadRequest,
    isReplacement: Boolean
  )(
    mediaIdToAddUsageTo: String
  )(implicit instance: Instance) = {
    gridClient.postUsage(
      usageType = "child",
      data = Json.obj(
        "dateAdded" -> uploadRequest.uploadTime.toString,
        "addedBy" -> uploadRequest.uploadedBy,
        "mediaId" -> mediaIdToAddUsageTo,
        "childMediaId" -> uploadRequest.imageId,
        "isReplacement" -> isReplacement,
      ),
      // we're using the innerServiceCall here rather than 'on behalf of' since this code is typically run when the
      // queue is processed, so we don't have reference to the original requester's auth
      authFn = auth.innerServiceCall
    )
  }

  private def fromUploadRequest(uploadRequest: UploadRequest)
                               (implicit logMarker: LogMarker, instance: Instance): Future[ImageUpload] = {
    val sideEffectDependencies = ImageUploadOpsDependencies(toImageUploadOpsCfg(config), imageOps,
      storeSource, storeThumbnail, storeOptimisedImage)
    Stopwatch.async("finalImage") {
      val finalImage = fromUploadRequestShared(uploadRequest, sideEffectDependencies, imageProcessor)
      uploadRequest.identifiers.foreach{
        case (ImageStorageProps.derivativeOfMediaIdsIdentifierKey, commaSeparatedMediaIdsToAddUsagesTo) =>
          commaSeparatedMediaIdsToAddUsagesTo.split(",").map(_.trim).foreach(
            addChildUsageToParentImage(uploadRequest, isReplacement = false)
          )
        case (ImageStorageProps.replacesMediaIdIdentifierKey, mediaIdToAddUsageTo) =>
          addChildUsageToParentImage(uploadRequest, isReplacement = true)(mediaIdToAddUsageTo)
      }
      finalImage.map(img => ImageUpload(uploadRequest, img))
    }
  }

  private def queueImageToEmbed(message: EmbedderMessage)(implicit logMarker: LogMarker): Unit = {
    maybeEmbedder match {
      case Some(embedder) =>
        logger.info(logMarker, s"Queueing image ${message.imageId} for embedding")
        embedder.queueImageToEmbed(message)
      case None => ()
    }
  }

  private def storeSource(storableOriginalImage: StorableOriginalImage)
                         (implicit logMarker: LogMarker) = store.store(storableOriginalImage)

  private def storeThumbnail(storableThumbImage: StorableThumbImage)
                            (implicit logMarker: LogMarker) = store.store(storableThumbImage)

  private def storeOptimisedImage(storableOptimisedImage: StorableOptimisedImage)
                                 (implicit logMarker: LogMarker) = store.store(storableOptimisedImage)

  def loadFile(digestedFile: DigestedFile,
               uploadedBy: String,
               identifiers: Map[String, String],
               uploadTime: DateTime,
               filename: Option[String],
               instance: Instance,
               isFeedUpload: Boolean)
              (implicit ec:ExecutionContext,
               logMarker: LogMarker): Future[UploadRequest] = Future {
    val DigestedFile(tempFile, id) = digestedFile

    val identifiersMap = identifiers
      .view
      .mapValues(_.toLowerCase)
      .toMap

    MimeTypeDetection.guessMimeType(tempFile) match {
      case util.Left(unsupported) =>
        logger.error(logMarker, s"Unsupported mimetype", unsupported)
        throw unsupported
      case util.Right(mimeType) =>
        logger.info(logMarker, s"Detected mimetype as $mimeType")
        UploadRequest(
          imageId = id,
          tempFile = tempFile,
          mimeType = Some(mimeType),
          uploadTime = uploadTime,
          uploadedBy = uploadedBy,
          identifiers = identifiersMap,
          uploadInfo = UploadInfo(filename, Some(isFeedUpload)),
          instance = instance
        )
    }
  }

  def storeFile(uploadRequest: UploadRequest)
               (implicit ec:ExecutionContext,
                logMarker: LogMarker, instance: Instance): Future[UploadStatusUri] = {

    logger.info(logMarker, "Storing file")

    for {
      imageUpload <- fromUploadRequest(uploadRequest)
      updateMessage = UpdateMessage(subject = Image, image = Some(imageUpload.image), instance = uploadRequest.instance)
      _ <- Future { notifications.publish(updateMessage) }
      // Send the optimised PNG to the embedder if there is one (e.g. for TIFFs),
      // otherwise send the original image.
      assetForEmbedder = imageUpload.image.optimisedPng match {
        case Some(optimisedPngAsset) =>
          logger.info(logMarker, s"Queueing optimised PNG instead of original for embedding")
          optimisedPngAsset
        case _ =>
          imageUpload.image.source
      }
      uriForEmbedder = assetForEmbedder.file
      s3BucketForEmbedder = uriForEmbedder.getHost.split('.').head
      s3KeyForEmbedder = uriForEmbedder.getPath.stripPrefix("/")
      mimeTypeForEmbedder = assetForEmbedder.mimeType.getOrElse(
        throw new Exception("Image for embedding has no mime type")
      ).name
      _ = queueImageToEmbed(EmbedderMessage(
        uploadRequest.imageId,
        mimeTypeForEmbedder,
        s3BucketForEmbedder,
        s3KeyForEmbedder,
      ))
      // TODO: centralise where all these URLs are constructed
    } yield
      UploadStatusUri(s"${config.rootUri(instance)}/uploadStatus/${uploadRequest.imageId}")
  }

  def restoreFile(uploadRequest: UploadRequest,
                  gridClient: GridClient,
                  onBehalfOfFn: WSRequest => WSRequest)
                 (implicit ec: ExecutionContext,
                  logMarker: LogMarker,
                  instance: Instance): Future[Unit] = for {
    imageUpload <- fromUploadRequest(uploadRequest)
    imageWithoutUserEdits = imageUpload.image
    imageWithUserEditsApplied <- ImageDataMerger.aggregate(imageWithoutUserEdits, gridClient, onBehalfOfFn)
    _ <- Future {
      notifications.publish(
        UpdateMessage(subject = Image, image = Some(imageWithUserEditsApplied), instance = uploadRequest.instance)
      )
    }
  } yield ()

}


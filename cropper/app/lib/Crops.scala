package lib

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.jextract.VipsRaw
import com.gu.mediaservice.lib.Files
import com.gu.mediaservice.lib.aws.{S3, S3Bucket}
import com.gu.mediaservice.lib.imaging.{ExportResult, ImageOperations}
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, Stopwatch}
import com.gu.mediaservice.model._

import java.io.File
import java.lang.foreign.Arena
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case object InvalidImage extends Exception("Invalid image cannot be cropped")
case object MissingMimeType extends Exception("Missing mimeType from source API")
case object InvalidCropRequest extends Exception("Crop request invalid for image dimensions")

case class MasterCrop(image: VImage, dimensions: Dimensions, aspectRatio: Float)

class Crops(config: CropperConfig, store: CropStore, imageOperations: ImageOperations, imageBucket: S3Bucket, s3: S3)(implicit ec: ExecutionContext) extends GridLogging {
  import Files._

  private val cropQuality = 75
  private val jpegMasterCropQuality = 95
  // For PNGs, Magick considers "quality" parameter as effort spent on compression - 1 meaning none, 100 meaning max.
  // We don't overly care about output crop file sizes here, but prefer a fast output, so turn it right down.
  private val pngMasterCropQuality = 1  // No effort spend compressing the PNG master

  def outputFilename(imageId: String, bounds: Bounds, outputWidth: Int, fileType: MimeType, isMaster: Boolean = false)(implicit instance: Instance): String = {
    val masterString: String = if (isMaster) "master/" else ""
    instance.id + "/" + s"$imageId/${Crop.getCropId(bounds)}/$masterString$outputWidth${fileType.fileExtension}"
  }

  private def createMasterCrop(
    apiImage: SourceImage,
    sourceFile: File,
    crop: Crop,
    metadata: ImageMetadata,
    orientationMetadata: Option[OrientationMetadata]
  )(implicit logMarker: LogMarker, arena: Arena): MasterCrop = {

    Stopwatch(s"creating master crop for ${apiImage.id}") {
      val source = crop.specification
    logger.info(logMarker, s"creating master crop for ${apiImage.id}")
    val masterImage = imageOperations.cropImageVips(
      sourceFile,
      source.bounds,
      metadata,
      orientationMetadata = orientationMetadata
    )

      //file: File <- imageOperations.appendMetadata(strip, metadata)
      val dimensions = Dimensions(source.bounds.width, source.bounds.height)
      val dirtyAspect = source.bounds.width.toFloat / source.bounds.height
      val aspect = crop.specification.aspectRatio.flatMap(AspectRatio.clean).getOrElse(dirtyAspect)

      MasterCrop(masterImage, dimensions, aspect)
    }
  }

  def deleteCrops(id: String)(implicit logMarker: LogMarker, instance: Instance): Future[Unit] = store.deleteCrops(id)

  private def dimensionsFromConfig(bounds: Bounds, aspectRatio: Float): List[Dimensions] = if (bounds.isPortrait)
      config.portraitCropSizingHeights.filter(_ <= bounds.height).map(h => Dimensions(math.round(h * aspectRatio), h))
    else
    config.landscapeCropSizingWidths.filter(_ <= bounds.width).map(w => Dimensions(w, math.round(w / aspectRatio)))

  def isWithinImage(bounds: Bounds, dimensions: Dimensions): Boolean = {
    logger.info(s"Validating crop bounds ($bounds) against dimensions: $dimensions")
    val positiveCoords       = List(bounds.x,     bounds.y     ).forall(_ >= 0)
    val strictlyPositiveSize = List(bounds.width, bounds.height).forall(_  > 0)
    val withinBounds = (bounds.x + bounds.width  <= dimensions.width ) &&
                       (bounds.y + bounds.height <= dimensions.height)

    positiveCoords && strictlyPositiveSize && withinBounds
  }

  def makeExport(apiImage: SourceImage, crop: Crop)(implicit logMarker: LogMarker, instance: Instance): Future[ExportResult] = {
    val source = crop.specification
    val mimeType = apiImage.source.mimeType.getOrElse(throw MissingMimeType)
    val secureFile = apiImage.source.file

    val key = imageBucket.keyFromS3URL(secureFile)
    val secureUrl = s3.signUrlTony(imageBucket, key)

    //val eventualResult = Stopwatch(s"making crop assets for ${apiImage.id} ${Crop.getCropId(source.bounds)}") {
    tempFileFromURL(secureUrl, "cropSource", "", config.tempDir).flatMap { sourceFile =>
      logger.info("Starting vips operations")
      implicit val arena: Arena = Arena.ofShared()
      val masterCrop = createMasterCrop(apiImage, sourceFile, crop, apiImage.metadata, apiImage.source.orientationMetadata)

      val isGraphic = ImageOperations.isGraphicVips(masterCrop.image)
      val hasAlpha = apiImage.fileMetadata.colourModelInformation.get("hasAlpha").flatMap(a => Try(a.toBoolean).toOption).getOrElse(true)
      val cropType = Crops.cropType(mimeType, isGraphic = isGraphic, hasAlpha = hasAlpha)


      // pngs are always lossless, so quality only means effort spent compressing them. We don't
      // care too much about filesize of master crops, so skip expensive compression to get faster cropping
      val masterQuality = if (mimeType == Png) pngMasterCropQuality else jpegMasterCropQuality

      // High quality rendering with minimal compression which will be used as the CDN resizer origin
      logger.info("Requesting master file save")
      val eventualMasterSaved = Future {
        val masterCropFile = File.createTempFile(s"crop-", s"${cropType.fileExtension}", config.tempDir) // TODO function for this
        imageOperations.saveImageToFile(masterCrop.image, cropType, masterQuality, masterCropFile, keep = Some(VipsRaw.VIPS_FOREIGN_KEEP_XMP))
        masterCropFile
      }

      // Static crops; higher compression
      logger.info("Requesting resize file saves")
      val outputDims = dimensionsFromConfig(source.bounds, masterCrop.aspectRatio) :+ masterCrop.dimensions
      val eventualResizes = imageOperations.createCrops(masterCrop.image, outputDims, apiImage.id, crop.specification.bounds, cropType, config.tempDir, cropQuality)

      // Store assets
      val eventualStoredMasterCropAsset = eventualMasterSaved.flatMap { masterCropFile =>
        // TODO delete temp file
        store.storeCropSizing(masterCropFile, outputFilename(apiImage.id, source.bounds, masterCrop.dimensions.width, cropType, isMaster = true), cropType, crop, masterCrop.dimensions)
      }
      val eventualStoredCropAssets = eventualResizes.flatMap { resizes =>
        // All vips operations have completed; we can close the arena
        arena.close()
        logger.info("Finished vips operations")

        val eventualStoredAssets = resizes.map { resize: (File, String, Dimensions) =>
          val file = resize._1
          val filename = resize._2
          val dimensions = resize._3
          logger.info(s"Storing crop for: $file, $filename, $cropType")

          for {
            sizing: Asset <- store.storeCropSizing(file, filename, cropType, crop, dimensions)
            _ <- delete(file)
          }
          yield sizing
        }
        Future.sequence(eventualStoredAssets)
      }

      eventualStoredMasterCropAsset.flatMap { masterSize =>
        eventualStoredCropAssets.flatMap { sizes =>
          Future.sequence(List(sourceFile).map(delete)).map { _ =>
            ExportResult(apiImage.id, masterSize, sizes.toList)
          }
        }
      }
    }
  }
}

object Crops extends GridLogging {
  /**
    * The aim here is to decide whether the crops should be JPEG or PNGs depending on a predicted quality/size trade-off.
    *  - If the image has transparency then it should always be a PNG as the transparency is not available in JPEG
    *  - If the image is not true colour then we assume it is a graphic that should be retained as a PNG
    */
  def cropType(mediaType: MimeType, isGraphic: Boolean, hasAlpha: Boolean): MimeType = {
    val outputAsPng = hasAlpha || isGraphic

    val decision = mediaType match {
      case Png if outputAsPng => Png
      case Tiff if outputAsPng => Png
      case _ => Jpeg
    }

    logger.info(s"Choose crop type for $mediaType, $isGraphic, $hasAlpha: " + decision)
    decision
  }
}

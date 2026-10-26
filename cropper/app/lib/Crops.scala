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

  private val cropQuality = 75d
  private val masterCropQuality = 95d
  // In libvips Q sets the quality target for the PNG quantiser. The range is 0–100 and the default is 100.
  // A lower Q lets the quantiser use fewer colours and accept more error. You get a smaller, lossier file with more banding.
  private val pngCropQuality = 70d

  def outputFilename(source: SourceImage, bounds: Bounds, outputWidth: Int, fileType: MimeType, isMaster: Boolean = false)(implicit instance: Instance): String = {
    val masterString: String = if (isMaster) "master/" else ""
    instance.id + "/" + s"${source.id}/${Crop.getCropId(bounds)}/$masterString$outputWidth${fileType.fileExtension}"
  }

  private def createMasterCrop(
    apiImage: SourceImage,
    sourceFile: File,
    crop: Crop,
    orientationMetadata: Option[OrientationMetadata]
  )(implicit logMarker: LogMarker, arena: Arena): Future[MasterCrop] = {
    Stopwatch.async(s"creating master crop for ${apiImage.id}") {
      val source = crop.specification
      val metadata = apiImage.metadata

      for {
        image <- imageOperations.cropImage(
          sourceFile, source.bounds, config.tempDir,
          orientationMetadata = orientationMetadata,
          metadata = metadata
        )
        dimensions = Dimensions(source.bounds.width, source.bounds.height)
        dirtyAspect = source.bounds.width.toFloat / source.bounds.height
        aspect = crop.specification.aspectRatio.flatMap(AspectRatio.clean).getOrElse(dirtyAspect)
      }
      yield MasterCrop(image, dimensions, aspect)
    }
  }

  private def createCrops(sourceImage: VImage, dimensionList: List[Dimensions], apiImage: SourceImage, crop: Crop, cropType: MimeType)(implicit logMarker: LogMarker, instance: Instance, arena: Arena): Future[List[(File, String, Dimensions)]] = {
    val quality = if (cropType == Png) pngCropQuality else cropQuality

    Stopwatch.async(s"creating crops for ${apiImage.id}") {
      Future.sequence(dimensionList.map { dimensions =>
        val cropLogMarker = logMarker ++ Map("crop-dimensions" -> s"${dimensions.width}x${dimensions.height}")
        val quantise = cropType == Png && !imageOperations.isGraphic(sourceImage)
        for {
          file <- imageOperations.resizeImage(sourceImage,
            dimensions,
            quality,
            config.tempDir,
            cropType,
            quantise = quantise
          )(cropLogMarker, arena)
          filename = outputFilename(apiImage, crop.specification.bounds, dimensions.width, cropType)
        }
        yield {
          (file, filename, dimensions)
        }
      })
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

    val key = imageBucket.keyFromURL(secureFile)
    val secureUrl = s3.signUrlTony(imageBucket, key)

    implicit val arena: Arena = Arena.ofShared()
    // Generate the crop assets on local disk
    val eventualCropAssets = Stopwatch.async(s"making crop local assets for ${apiImage.id} ${Crop.getCropId(source.bounds)}") {
      for {
        sourceFile <- tempFileFromURL(secureUrl, "cropSource", "", config.tempDir)
        masterCrop <- createMasterCrop(apiImage, sourceFile, crop, apiImage.source.orientationMetadata)

        isGraphic = imageOperations.isGraphic(masterCrop.image)
        hasAlpha = imageOperations.hasAlpha(masterCrop.image)
        cropType = Crops.cropType(mimeType, isGraphic = isGraphic, hasAlpha = hasAlpha)

        // pngs are always lossless, so quality only means effort spent compressing them. We don't
        // care too much about filesize of master crops, so skip expensive compression to get faster cropping
        quality = if (cropType == Png) pngCropQuality else masterCropQuality

        // Start the master save and the resizes together so they render in parallel
        eventualMasterCropFile = Future {
          val masterCropFile = File.createTempFile(s"crop-", s"${cropType.fileExtension}", config.tempDir) // TODO function for this
          imageOperations.saveImageToFile(masterCrop.image, cropType, quality, masterCropFile, quantise = false, keep = Some(VipsRaw.VIPS_FOREIGN_KEEP_XMP()))
        }
        outputDims = dimensionsFromConfig(source.bounds, masterCrop.aspectRatio) :+ masterCrop.dimensions
        eventualResizes = createCrops(masterCrop.image, outputDims, apiImage, crop, cropType)

        // Wait for both before storing anything; we don't want to send a partial set
        masterCropFile <- eventualMasterCropFile
        resizes <- eventualResizes

        masterCropFilename = outputFilename(apiImage, source.bounds, masterCrop.dimensions.width, cropType, isMaster = true)
        eventualMasterAsset: Future[Asset] = for {
          masterAsset <- store.storeCropSizing(masterCropFile, masterCropFilename, cropType, crop, masterCrop.dimensions)
          _ <- delete(masterCropFile)
        } yield {
          masterAsset
        }

        eventualResizeAssets: Future[List[Asset]] = Future.sequence {
          resizes.map { resize =>
            val file = resize._1
            val filename = resize._2
            val dimensions = resize._3
            logger.info(s"Storing crop for: $file, $filename, $cropType")
            for {
              sizing <- store.storeCropSizing(file, filename, cropType, crop, dimensions)
              _ <- delete(file)
            } yield {
              sizing
            }
          }
        }
      } yield {
        logger.info("Image operations have finished; closing makeExport arena.")
        arena.close()
        sourceFile.delete()
        (eventualMasterAsset, eventualResizeAssets)
      }
    }

    // Once the assets have been rendered to local files we can close the arena
    // to release the arena memory and then do the S3 uploads.
    Stopwatch.async(s"Uploading crop assets to S3") {
      for {
        sentCropFiles <- eventualCropAssets
        masterAsset <- sentCropFiles._1
        resizeAssets <- sentCropFiles._2
      }
      yield ExportResult(apiImage.id, masterAsset, resizeAssets)
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

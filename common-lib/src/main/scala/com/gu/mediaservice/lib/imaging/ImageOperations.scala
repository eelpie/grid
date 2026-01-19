package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.enums.{VipsIntent, VipsInterpretation}
import app.photofox.vipsffm.{VImage, VipsHelper, VipsOption}
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.imaging.ImageOperations.thumbMimeType
import com.gu.mediaservice.lib.imaging.im4jwrapper.{ExifTool, ImageMagick}
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, Stopwatch, addLogMarkers}
import com.gu.mediaservice.model._
import org.im4java.core.IMOperation

import java.io._
import java.lang.foreign.Arena
import scala.concurrent.{ExecutionContext, Future}


case class ExportResult(id: String, masterCrop: Asset, othersizings: List[Asset])
class UnsupportedCropOutputTypeException extends Exception

class ImageOperations(playPath: String) extends GridLogging {
  import ExifTool._
  import ImageMagick._

  private def profilePath(fileName: String): String = s"$playPath/$fileName"

  private def rgbProfileLocation(optimised: Boolean): String = {
    if (optimised)
      profilePath("facebook-TINYsRGB_c2.icc")
    else
      profilePath("srgb.icc")
  }

  private val profileLocations = Map(
    "RGB" -> profilePath("srgb.icc"),
    "CMYK" -> profilePath("cmyk.icc"),
    "Greyscale" -> profilePath("grayscale.icc")
  )

  private def tagFilter(metadata: ImageMetadata) = {
    Map[String, Option[String]](
      "Copyright" -> metadata.copyright,
      "Credit" -> metadata.credit,
      "OriginalTransmissionReference" -> metadata.suppliersReference
    ).collect { case (key, Some(value)) => (key, value) }
  }

  def cropImageVips(
                     sourceFile: File,
                     bounds: Bounds,
                     orientationMetadata: Option[OrientationMetadata]
                   )(implicit logMarker: LogMarker, arena: Arena): VImage = {
    // Read source image
    val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

    // Orient
    val rotated = orientationMetadata.map(_.orientationCorrection()).map { angle =>
      image.rotate(angle)
    }.getOrElse {
      image
    }
    // TODO strip meta data
    // Output colour profile
    val cropped = rotated.extractArea(bounds.x, bounds.y, bounds.width, bounds.height)

    // If we saw and ICC profile than we will need to transform
    val needsICCTransform = VipsHelper.image_get_typeof(arena, image.getUnsafeStructAddress, "icc-profile-data") != 0
    val correctedForICCProfile = if (needsICCTransform ) {
      cropped.iccTransform("srgb",
        VipsOption.Enum("intent",VipsIntent.INTENT_PERCEPTUAL),     // Helps with CMYK; see https://github.com/libvips/libvips/issues/1110
      )
    } else {
      // LAB gets corrupted by a needless icc_transform
      cropped
    }

    correctedForICCProfile
  }


  // Updates metadata on existing file
  def appendMetadata(sourceFile: File, metadata: ImageMetadata): Future[File] = {
    runExiftoolCmd(
      setTags(tagSource(sourceFile))(tagFilter(metadata))
      ).map(_ => sourceFile)
  }

  def resizeImageVips(
                       sourceImage: VImage,
                       dimensions: Dimensions,
                       quality: Int = 100,
                       outputFile: File,
                       fileType: MimeType,
                       sourceDimensions: Dimensions
                     )(implicit logMarker: LogMarker, arena: Arena): File = {

    val scale = dimensions.width.toDouble / sourceDimensions.width.toDouble
    val resized = sourceImage.resize(scale)

    saveImageToFile(resized, fileType, quality, outputFile, quantise = true)
  }

  private def orient(op: IMOperation, orientationMetadata: Option[OrientationMetadata]): IMOperation = {
    logger.info("Correcting for orientation: " + orientationMetadata)
    orientationMetadata.map(_.orientationCorrection()) match {
      case Some(angle) => rotate(op)(angle)
      case _ => op
    }
  }

  val interlacedHow = "Line"
  val backgroundColour = "#333333"

  /**
   * Given a source file containing an image (the 'browser viewable' file),
   * construct a thumbnail file in the provided temp directory, and return
   * the file with metadata about it.
   *
   * @param browserViewableImage
   * @param width               Desired with of thumbnail
   * @param qual                Desired quality of thumbnail
   * @param outputFile          Location to create thumbnail file
   * @param orientationMetadata OrientationMetadata for rotation correction
   * @return The file created and the mimetype of the content of that file and it's dimensions, in a future.
   */
  def createThumbnailVips(browserViewableImage: BrowserViewableImage,
                          width: Int,
                          qual: Double = 100d,
                          outputFile: File,
                          orientationMetadata: Option[OrientationMetadata]
                         )(implicit logMarker: LogMarker): Future[(File, MimeType, Option[Dimensions])] = {
    Future {
      val stopwatch = Stopwatch.start
      val arena = Arena.ofConfined

      try {
        val thumbnail = VImage.thumbnail(arena, browserViewableImage.file.getAbsolutePath, width,
          VipsOption.Boolean("auto-rotate", false),
          VipsOption.Enum("intent", VipsIntent.INTENT_PERCEPTUAL),
          VipsOption.String("export-profile", "srgb")
        )
        val rotated = orientationMetadata.map(_.orientationCorrection()).map { angle =>
          logger.info("Rotating thumbnail: " + angle)
          thumbnail.rotate(angle)
        }.getOrElse {
          thumbnail
        }
        logger.info("Created thumbnail: " + rotated.getWidth + "x" + rotated.getHeight)
        saveImageToFile(rotated, Jpeg, qual.toInt, outputFile)

        val thumbDimensions = Some(Dimensions(rotated.getWidth, rotated.getHeight))
        arena.close()

        logger.info(addLogMarkers(stopwatch.elapsed), "Finished creating thumbnail")
        (outputFile, thumbMimeType, thumbDimensions)

      } catch {
        case e: Throwable =>
          arena.close()
          throw e
      }

    }.recoverWith {
      case e: Throwable =>
        logger.error("Error creating thumbnail", e)
        Future.failed(e)
    }
  }

  def saveImageToFile(image: VImage, mimeType: MimeType, quality: Int, outputFile: File, quantise: Boolean = false): File = {
    logger.info(s"Saving image as $mimeType to file: " + outputFile.getAbsolutePath)
    mimeType match {
      case Jpeg =>
        image.jpegsave(outputFile.getAbsolutePath,
          VipsOption.Int("Q", quality),
          //VipsOption.Boolean("optimize-scans", true),
          VipsOption.Boolean("optimize-coding", true),
          //VipsOption.Boolean("interlace", true),
          //VipsOption.Boolean("trellis-quant", true),
          // VipsOption.Int("quant-table", 3),
          VipsOption.Boolean("strip", true)
        )
        outputFile

      case Png =>
        // We are allowed to quantise PNG crops but not the master
        if (quantise) {
          image.pngsave(outputFile.getAbsolutePath,
            VipsOption.Boolean("palette", true),
            VipsOption.Int("Q", quality),
            VipsOption.Int("effort", 1),
            //VipsOption.Int("compression", 6),
            VipsOption.Int("bitdepth", 8),
            VipsOption.Boolean("strip", true)
          )
        } else {
          image.pngsave(outputFile.getAbsolutePath,
            //VipsOption.Int("compression", 6),
            VipsOption.Boolean("strip", true)
          )
        }
        outputFile

      case _ =>
        logger.error(s"Save to $mimeType is not supported.")
        throw new UnsupportedCropOutputTypeException
    }
  }
}

object ImageOperations extends GridLogging {
  val thumbMimeType = Jpeg
  val optimisedMimeType = Png

  def getImageInformation(sourceFile: File)(implicit ec: ExecutionContext, logMarker: LogMarker): Future[(Option[Dimensions], Option[OrientationMetadata], Option[String], Map[String, String])] = {
    val stopwatch = Stopwatch.start
    Future {
      var dimensions: Option[Dimensions] = None
      var maybeExifOrientationWhichTransformsImage: Option[OrientationMetadata] = None
      var colourModel: Option[String] = None
      var colourModelInformation: Map[String, String] = Map.empty

      val arena = Arena.ofConfined
      try {
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

        dimensions = Some(Dimensions(width = image.getWidth, height = image.getHeight))

        val exifOrientation = VipsHelper.image_get_orientation(image.getUnsafeStructAddress)
        val orientation = Some(OrientationMetadata(
          exifOrientation = Some(exifOrientation)
        ))
        maybeExifOrientationWhichTransformsImage = Seq(orientation).flatten.find(_.transformsImage())

        // TODO better way to go straight from int to enum?
        val maybeInterpretation = VipsInterpretation.values().toSeq.find(_.getRawValue == VipsHelper.image_get_interpretation(image.getUnsafeStructAddress))
        colourModel = maybeInterpretation match {
          case Some(VipsInterpretation.INTERPRETATION_B_W) => Some("Greyscale")
          case Some(VipsInterpretation.INTERPRETATION_CMYK) => Some("CMYK")
          case Some(VipsInterpretation.INTERPRETATION_LAB) => Some("LAB")
          case Some(VipsInterpretation.INTERPRETATION_LABS) => Some("LAB")
          case Some(VipsInterpretation.INTERPRETATION_RGB16) => Some("RGB")
          case Some(VipsInterpretation.INTERPRETATION_sRGB) => Some("RGB")
          case _ => None
        }

        colourModelInformation = Map {
          "hasAlpha" -> image.hasAlpha.toString // TODO push to imageoperations for testing
        }
      } catch {
        case e: Exception =>
          logger.error("Error during getImageInformation", e)
          throw e
      }
      arena.close()

      (dimensions, maybeExifOrientationWhichTransformsImage, colourModel, colourModelInformation)
    }.map { result =>
      logger.info(addLogMarkers(stopwatch.elapsed), "Finished getImageInformation")
      result
    }
  }

  def hasAlpha(image: VImage)(implicit arena: Arena): Boolean = image.hasAlpha

  def isGraphicVips(image: VImage)(implicit arena: Arena): Boolean = {
    val numberOfBands = VipsHelper.image_get_bands(image.getUnsafeStructAddress)
   logger.info("Number of bands: " + numberOfBands)
    // Indexed plus alpha would be 2 bands

    val format = VipsHelper.image_get_format(image.getUnsafeStructAddress)
    logger.info("Format: " + format)

    val paletteType = VipsHelper.image_get_typeof(arena, image.getUnsafeStructAddress, "palette")
    logger.info("Palette type: " + paletteType)

    paletteType > 0 || numberOfBands < 3
  }
}

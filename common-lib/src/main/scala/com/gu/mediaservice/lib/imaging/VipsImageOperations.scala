package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.enums.{VipsIntent, VipsInterpretation}
import app.photofox.vipsffm.{VImage, VipsHelper, VipsOption}
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, Stopwatch, addLogMarkers}
import com.gu.mediaservice.model._

import java.io._
import java.lang.foreign.Arena
import scala.concurrent.Future

class VipsImageOperations(playPath: String) extends GridLogging with ImageOperations {

  def cropImage(
                 sourceFile: File,
                 sourceMimeType: Option[MimeType],
                 bounds: Bounds,
                 qual: Double = 100d,
                 tempDir: File,
                 iccColourSpace: Option[String],
                 colourModel: Option[String],
                 fileType: MimeType,
                 isTransformedFromSource: Boolean,
                 orientationMetadata: Option[OrientationMetadata]
               )(implicit logMarker: LogMarker): Future[File] = ???

  def appendMetadata(sourceFile: File, metadata: ImageMetadata): Future[File] = ???

  def resizeImage(
                   sourceFile: File,
                   sourceMimeType: Option[MimeType],
                   dimensions: Dimensions,
                   qual: Double = 100d,
                   tempDir: File,
                   fileType: MimeType
                 )(implicit logMarker: LogMarker): Future[File] = ???

  def optimiseImage(resizedFile: File, mediaType: MimeType)(implicit logMarker: LogMarker): File = ???

  def optimiseImage(resizedFile: File, mediaType: MimeType)(implicit logMarker: LogMarker): File = mediaType match {
    case Png =>
      val fileName: String = resizedFile.getAbsolutePath

      val optimisedImageName: String = fileName.split('.')(0) + "optimised.png"
      Stopwatch("pngquant") {
        Seq("pngquant", "-s10", "--quality", "1-85", fileName, "--output", optimisedImageName).!
      }

      new File(optimisedImageName)
    case Jpeg => resizedFile

    // This should never happen as we only ever crop as PNG or JPEG. See `Crops.cropType` and `CropsTest`
    // TODO We should create a `CroppingMimeType` to enforce this at the type level.
    //  However we'd need to change the `Asset` model as source image and crop use this model
    //  and a source can legally be a `Tiff`. It's not a small change...
    case Tiff =>
      logger.error("Attempting to optimize a Tiff crop. Cropping as Tiff is not supported.")
      throw new UnsupportedCropOutputTypeException
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
  def createThumbnail(browserViewableImage: BrowserViewableImage,
                      width: Int,
                      qual: Double = 100d,
                      outputFile: File,
                      orientationMetadata: Option[OrientationMetadata]
                     )(implicit logMarker: LogMarker): Future[(File, MimeType, Option[Dimensions])] = {
    val stopwatch = Stopwatch.start

    Future {
      var thumbDimensions: Option[Dimensions] = None
      val arena = Arena.ofConfined

      try {
        val thumbnail = VImage.thumbnail(arena, browserViewableImage.file.getAbsolutePath, width,
          VipsOption.Boolean("auto-rotate", false),
          VipsOption.Enum("intent",VipsIntent.INTENT_PERCEPTUAL),
          VipsOption.String("export-profile", "srgb")
        )
        val rotated = orientationMetadata.map(_.orientationCorrection()).map { angle =>
          logger.info("Rotating thumbnail: " + angle)
          thumbnail.rotate(angle)
        }.getOrElse {
          thumbnail
        }
        logger.info("Created thumbnail: " + rotated.getWidth + "x" + rotated.getHeight)
        thumbDimensions = Some(Dimensions(rotated.getWidth, rotated.getHeight))

        saveImageToFile(rotated, qual.toInt, outputFile)
      } catch {
        case e: Exception =>
          logger.error("Error during createThumbnail", e)
          arena.close()
          throw e
      }
      arena.close()

      logger.info(addLogMarkers(stopwatch.elapsed), "Finished creating thumbnail")
      (outputFile, thumbMimeType, thumbDimensions)
    }
  }

  private def saveImageToFile(image: VImage, qual: Double, outputFile: File): File = {
    logger.info(s"Saving image to file: " + outputFile.getAbsolutePath)
    image.jpegsave(outputFile.getAbsolutePath,
      VipsOption.Int("Q", qual.toInt),
      //VipsOption.Boolean("optimize-scans", true),
      VipsOption.Boolean("optimize-coding", true),
      //VipsOption.Boolean("interlace", true),
      //VipsOption.Boolean("trellis-quant", true),
      // VipsOption.Int("quant-table", 3),
      VipsOption.Boolean("strip", true)
    )
    outputFile
  }

  def getImageInformation(sourceFile: File)(implicit logMarker: LogMarker): Future[(Option[Dimensions], Option[OrientationMetadata], Option[String], Map[String, String])] = {
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
          "hasAlpha" -> image.hasAlpha.toString
        }
      } catch {
        case e: Exception =>
          logger.error("Error during getImageInformation", e)
          arena.close()
          throw e
      }
      arena.close()

      (dimensions, maybeExifOrientationWhichTransformsImage, colourModel, colourModelInformation)
    }.map { result =>
      logger.info(addLogMarkers(stopwatch.elapsed), "Finished getImageInformation")
      result
    }
  }

}

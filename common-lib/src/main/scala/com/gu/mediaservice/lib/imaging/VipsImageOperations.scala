package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.enums.VipsInterpretation
import app.photofox.vipsffm.{VImage, Vips, VipsHelper, VipsOption}
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.imaging.im4jwrapper.ImageMagick.ctx
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, Stopwatch, addLogMarkers}
import com.gu.mediaservice.model._

import java.io._
import scala.concurrent.Future


class VipsImageOperations extends GridLogging with ImageOperations {

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

  def createThumbnail(browserViewableImage: BrowserViewableImage,
                      width: Int,
                      qual: Double = 100d,
                      outputFile: File,
                      orientationMetadata: Option[OrientationMetadata]
                     )(implicit logMarker: LogMarker): Future[(File, MimeType)] = {
    val stopwatch = Stopwatch.start

    Future {
      Vips.run { arena =>
        try {
          val thumbnail = VImage.thumbnail(arena, browserViewableImage.file.getAbsolutePath, width,
            VipsOption.Boolean("auto-rotate", false),
            VipsOption.String("export-profile", "srgb")
          )
          val rotated = orientationMetadata.map(_.orientationCorrection()).map { angle =>
            logger.info("Rotating thumbnail: " + angle)
            thumbnail.rotate(angle)
          }.getOrElse {
            thumbnail
          }

          saveImageToFile(rotated, qual, outputFile)
        } catch {
          case e: Exception =>
            logger.error("Error during createThumbnail", e)
            throw e
        }
      }
      logger.info(addLogMarkers(stopwatch.elapsed), "Finished creating thumbnail")
      (outputFile, thumbMimeType)
    }
  }

  def transformImage(sourceFile: File, sourceMimeType: Option[MimeType], tempDir: File)(implicit logMarker: LogMarker): Future[(File, MimeType)] = ???

  def identifyColourModel(sourceFile: File, mimeType: MimeType)(implicit logMarker: LogMarker): Future[Option[String]] = {
    val stopWatch = Stopwatch.start
    Future {
      var result: Option[String] = None
      Vips.run { arena =>
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
        // TODO better way to go straight from int to enum?
        val maybeInterpretation = VipsInterpretation.values().toSeq.find(_.getRawValue == VipsHelper.image_get_interpretation(image.getUnsafeStructAddress))
        result = maybeInterpretation match {
          case Some(VipsInterpretation.INTERPRETATION_B_W) => Some("Greyscale")
          case Some(VipsInterpretation.INTERPRETATION_CMYK) => Some("CMYK")
          case Some(VipsInterpretation.INTERPRETATION_LAB) => Some("LAB")
          case Some(VipsInterpretation.INTERPRETATION_LABS) => Some("LAB")
          case Some(VipsInterpretation.INTERPRETATION_RGB16) => Some("RGB")
          case Some(VipsInterpretation.INTERPRETATION_sRGB) => Some("RGB")
          case _ => None
        }
      }
      result

    }.map { result =>
      logger.info(addLogMarkers(stopWatch.elapsed), "Finished identifyColourModel")
      result
    }
  }

  def getColorModelInformation(sourceFile: File)(implicit logMarker: LogMarker): Future[Map[String, String]] = {
    val stopWatch = Stopwatch.start
    Future {
      var result: Map[String, String] = Map.empty
      Vips.run { arena =>
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
        result = Map {
          "hasAlpha" -> image.hasAlpha.toString
        }
      }
      result
    }.map { result =>
      logger.info(addLogMarkers(stopWatch.elapsed), "Finished getColorModelInformation")
      result
    }
  }

  def dimensionsAndOrientation(sourceFile: File): Future[(Option[Dimensions], Option[OrientationMetadata])] = {
    Future {
      var dimensions: Option[Dimensions] = None
      var maybeExifOrientationWhichTransformsImage: Option[OrientationMetadata] = None
      Vips.run { arena =>
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

        dimensions = Some(Dimensions(width = image.getWidth, height = image.getHeight))

        val exifOrientation = VipsHelper.image_get_orientation(image.getUnsafeStructAddress)
        val orientation = Some(OrientationMetadata(
          exifOrientation = Some(exifOrientation)
        ))
        maybeExifOrientationWhichTransformsImage = Seq(orientation).flatten.find(_.transformsImage())
      }

      (dimensions, maybeExifOrientationWhichTransformsImage)
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

}

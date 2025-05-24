package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.enums.{VipsIntent, VipsInterpretation}
import app.photofox.vipsffm.{VImage, VipsHelper, VipsOption}
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.imaging.im4jwrapper.ImageMagick.ctx
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, Stopwatch, addLogMarkers}
import com.gu.mediaservice.model._

import java.io._
import java.lang.foreign.Arena
import scala.concurrent.Future


class VipsImageOperations extends GridLogging with ImageOperations {

  def cropImage(
                     sourceFile: File,
                     sourceMimeType: Option[MimeType],
                     bounds: Bounds,
                     qual: Double = 100d,
                     tempDir: File,
                     iccColourSpace: Option[String],
                     fileType: MimeType,
                     isTransformedFromSource: Boolean,
                     orientationMetadata: Option[OrientationMetadata]
                   )(implicit logMarker: LogMarker, arena: Arena): (File, VImage) = {
    // Read source image
    val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
    // Orient
    val rotated = orientationMetadata.map(_.orientationCorrection()).map { angle =>
      image.rotate(angle)
    }.getOrElse {
      image
    }
    // TODO correct colour
    // TODO strip meta data
    // Output colour profile
    val cropped = rotated.extractArea(bounds.x, bounds.y, bounds.width, bounds.height)
    // TODO depth adjust

    // TODO separate this local file create from the vips master image create
    val outputFile = File.createTempFile(s"crop-", s"${fileType.fileExtension}", tempDir) // TODO function for this
    logger.info("Saving master crop tmp file to: " + outputFile.getAbsolutePath)
    cropped.jpegsave(outputFile.getAbsolutePath,
      VipsOption.Int("Q", qual.toInt),
      //VipsOption.Boolean("optimize-scans", true),
      //VipsOption.Boolean("optimize-coding", true),
      //VipsOption.Boolean("interlace", true),
      //VipsOption.Boolean("trellis-quant", true),
      // VipsOption.Int("quant-table", 3),
      VipsOption.Boolean("strip", true)
    )

    (outputFile, cropped)
  }

  def appendMetadata(sourceFile: File, metadata: ImageMetadata): Future[File] = ???

  def resizeImage(
                       sourceImage: VImage,
                       sourceMimeType: Option[MimeType],
                       dimensions: Dimensions,
                       qual: Double = 100d,
                       tempDir: File,
                       fileType: MimeType,
                       sourceDimensions: Dimensions
                     )(implicit logMarker: LogMarker, arena: Arena): File = {

    val scale = dimensions.width.toDouble / sourceDimensions.width.toDouble
    val resized = sourceImage.resize(scale)

    val outputFile = File.createTempFile(s"resize-", s"${fileType.fileExtension}", tempDir) // TODO function for this
    logger.info("Saving resized crop as JPEG tmp file to: " + outputFile.getAbsolutePath)

    fileType match {
      case Jpeg =>
        resized.jpegsave(outputFile.getAbsolutePath,
          VipsOption.Int("Q", qual.toInt),
          //VipsOption.Boolean("optimize-scans", true),
          //VipsOption.Boolean("optimize-coding", true),
          //VipsOption.Boolean("interlace", true),
          //VipsOption.Boolean("trellis-quant", true),
          // VipsOption.Int("quant-table", 3),
          VipsOption.Boolean("strip", false)
        )
        outputFile

      case Png =>
        // val optimisedImageName: String = fileName.split('.')(0) + "optimised.png"
        //      Seq("pngquant","-s8",  "--quality", "1-85", fileName, "--output", optimisedImageName).!
        resized.pngsave(outputFile.getAbsolutePath,
          VipsOption.Int("Q", qual.toInt),
          VipsOption.Boolean("strip", false)
        )
        outputFile

      case _ =>
        logger.error(s"Cropping to $fileType is not supported.")
        throw new UnsupportedCropOutputTypeException
    }
  }

  def optimiseImage(resizedFile: File, mediaType: MimeType)(implicit logMarker: LogMarker): File = ???

  def createThumbnail(browserViewableImage: BrowserViewableImage,
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

        val inMemoryCopy = thumbnail.copyMemory()

        val rotated = orientationMetadata.map(_.orientationCorrection()).map { angle =>
          logger.info("Rotating thumbnail: " + angle)
          inMemoryCopy.rotate(angle)
        }.getOrElse {
          inMemoryCopy
        }
        logger.info("Created thumbnail: " + rotated.getWidth + "x" + rotated.getHeight)
        saveImageToFile(rotated, Jpeg, qual, outputFile)

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

  def transformImage(sourceFile: File, sourceMimeType: Option[MimeType], tempDir: File)(implicit logMarker: LogMarker): Future[(File, MimeType)] = ???

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

  def hasAlpha(image: VImage)(implicit arena: Arena): Boolean = image.hasAlpha

  def saveImageToFile(image: VImage, mimeType: MimeType, qual: Double, outputFile: File, quantise: Boolean = false): File = {
    logger.info(s"Saving image as $mimeType to file: " + outputFile.getAbsolutePath)
    mimeType match {
      case Jpeg =>
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

      case Png =>
        // We are allowed to quantise PNG crops but not the master
        if (quantise) {
          image.pngsave(outputFile.getAbsolutePath,
            VipsOption.Boolean("palette", true),
            VipsOption.Int("Q", qual.toInt),
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

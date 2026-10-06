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
                     colourModel: Option[String],
                     fileType: MimeType,
                     isTransformedFromSource: Boolean,
                     orientationMetadata: Option[OrientationMetadata]
                   )(implicit logMarker: LogMarker): Future[File] = {

    Future {
      val arena = Arena.ofConfined

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

      val master = correctedForICCProfile

      // TODO separate this local file create from the vips master image create
      val outputFile = File.createTempFile(s"crop-", s"${fileType.fileExtension}", tempDir) // TODO function for this
      logger.info("Saving master crop tmp file to: " + outputFile.getAbsolutePath)
      master.jpegsave(outputFile.getAbsolutePath,
        VipsOption.Int("Q", qual.toInt),
        //VipsOption.Boolean("optimize-scans", true),
        //VipsOption.Boolean("optimize-coding", true),
        //VipsOption.Boolean("interlace", true),
        //VipsOption.Boolean("trellis-quant", true),
        // VipsOption.Int("quant-table", 3),
        VipsOption.Boolean("strip", true)
      )

      arena.close()
      outputFile
    }
  }

  def appendMetadata(sourceFile: File, metadata: ImageMetadata): Future[File] = ???

  def resizeImage(
                       sourceFile: File,
                       sourceMimeType: Option[MimeType],
                       dimensions: Dimensions,
                       qual: Double = 100d,
                       tempDir: File,
                       fileType: MimeType
                     )(implicit logMarker: LogMarker): Future[File] = {

    Future {
      val arena = Arena.ofConfined

      val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

      val scale = dimensions.width.toDouble / image.getWidth.toDouble
      val resized = image.resize(scale)

      val outputFile = File.createTempFile(s"resize-", s"${fileType.fileExtension}", tempDir) // TODO function for this
      logger.info("Saving resized crop as JPEG tmp file to: " + outputFile.getAbsolutePath)

      saveImageToFile(resized, fileType, qual, outputFile)
      arena.close()
      outputFile
    }
  }

  def optimiseCrop(input: File, mediaType: MimeType)(implicit logMarker: LogMarker): File = {
    val arena = Arena.ofConfined
    try {
      val fileName: String = input.getAbsolutePath
      val optimisedImageName: String = fileName.split('.')(0) + "optimised.png"
      val outputFile = new File(optimisedImageName) // TODO this is awful - push up!

      val image = VImage.newFromFile(arena, input.getAbsolutePath)
      // If we saw and ICC profile than we will need to transform
      val needsICCTransform = VipsHelper.image_get_typeof(arena, image.getUnsafeStructAddress, "icc-profile-data") != 0
      val correctedForICCProfile = if (needsICCTransform) {
        image.iccTransform("srgb",
          VipsOption.Enum("intent", VipsIntent.INTENT_PERCEPTUAL), // Helps with CMYK; see https://github.com/libvips/libvips/issues/1110
        )
      } else {
        // LAB gets corrupted by a needless icc_transform
        image
      }

      saveImageToFile(correctedForICCProfile: VImage, ImageOperations.optimisedMimeType, 85, outputFile, quantise = true)
      arena.close()
      outputFile

    } catch {
      case _: Exception =>
        arena.close()
        throw new Exception(s"Failed to optimise file ${input.getAbsolutePath}")
    }
  }

  def createThumbnail(browserViewableImage: BrowserViewableImage,
                      width: Int,
                      qual: Double = 100d,
                      outputFile: File,
                      iccColourSpace: Option[String],
                      colourModel: Option[String],
                      orientationMetadata: Option[OrientationMetadata]
                     )(implicit logMarker: LogMarker): Future[(File, MimeType)] = {
    val stopwatch = Stopwatch.start

    Future {
      val arena = Arena.ofConfined

      try {
        val thumbnail = VImage.thumbnail(arena, browserViewableImage.file.getAbsolutePath, width,
          VipsOption.Boolean("auto-rotate", false),
          VipsOption.Enum("intent",VipsIntent.INTENT_PERCEPTUAL),
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

      } catch {
        case e: Exception =>
          logger.error("Error during createThumbnail", e)
          arena.close()
          throw e
      }
      arena.close()

      logger.info(addLogMarkers(stopwatch.elapsed), "Finished creating thumbnail")
      (outputFile, thumbMimeType)
    }
  }

  def transformImage(sourceFile: File, sourceMimeType: Option[MimeType], tempDir: File)(implicit logMarker: LogMarker): Future[(File, MimeType)] = ???

  def identifyColourModel(sourceFile: File, mimeType: MimeType)(implicit logMarker: LogMarker): Future[Option[String]] = {
    val stopWatch = Stopwatch.start

    Future {
      val arena = Arena.ofConfined
      var result: Option[String] = None
      try {
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
      } catch {
        case e: Exception =>
          logger.error("Error during createThumbnail", e)
          arena.close()
          throw e
      }
      arena.close()
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

      val arena = Arena.ofConfined
      try {
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
        result = Map {
          "hasAlpha" -> image.hasAlpha.toString
        }
      } catch {
        case e: Exception =>
          logger.error("Error during createThumbnail", e)
          arena.close()
          throw e
      }
      arena.close()
      result
    }.map { result =>
      logger.info(addLogMarkers(stopWatch.elapsed), "Finished getColorModelInformation")
      result
    }
  }

  def dimensions(sourceFile: File): Future[Option[Dimensions]] = {
    Future {
      var dimensions: Option[Dimensions] = None

      val arena = Arena.ofConfined
      try {
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
        val width = image.getWidth
        val height = image.getHeight
        dimensions = Some(Dimensions(width = width, height = height))
      } catch {
        case e: Exception =>
          logger.error("Error during createThumbnail", e)
          arena.close()
          throw e
      }
      arena.close()
      dimensions
    }
  }

  def orientation(sourceFile: File): Future[Option[OrientationMetadata]] = {
    Future {
      var orientation: Option[OrientationMetadata] = None

      val arena = Arena.ofConfined
      try {
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
        val exifOrientation = VipsHelper.image_get_orientation(image.getUnsafeStructAddress)
        orientation = Some(OrientationMetadata(
          exifOrientation = Some(exifOrientation)
        ))
      } catch {
        case e: Exception =>
          logger.error("Error during createThumbnail", e)
          arena.close()
          throw e
      }
      arena.close()
      Seq(orientation).flatten.find(_.transformsImage())
    }
  }

  def hasAlpha(image: VImage)(implicit arena: Arena): Boolean = image.hasAlpha

  def isGraphic(image: VImage)(implicit arena: Arena): Boolean = {
    val numberOfBands = VipsHelper.image_get_bands(image.getUnsafeStructAddress)
   logger.info("Number of bands: " + numberOfBands)
    // Indexed plus alpha would be 2 bands

    val format = VipsHelper.image_get_format(image.getUnsafeStructAddress)
    logger.info("Format: " + format)

    val paletteType = VipsHelper.image_get_typeof(arena, image.getUnsafeStructAddress, "palette")
    logger.info("Palette type: " + paletteType)

    paletteType > 0 || numberOfBands < 3
  }

  private def saveImageToFile(image: VImage, mimeType: MimeType, qual: Double, outputFile: File, quantise: Boolean = false): File = {
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

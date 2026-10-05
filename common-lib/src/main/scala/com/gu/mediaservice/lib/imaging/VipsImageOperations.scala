package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.enums.{VipsIntent, VipsInterpretation}
import app.photofox.vipsffm.jextract.VipsRaw
import app.photofox.vipsffm.{VImage, VipsHelper, VipsOption}
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.imaging.im4jwrapper.ImageMagick.ctx
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
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

  def optimiseCrop(resizedFile: File, mediaType: MimeType)(implicit logMarker: LogMarker): File = {
    logger.info(s"optimiseCrop called on {${resizedFile.getAbsolutePath} with crop mediaType: $mediaType")
    // The pipeline has offered this step as a chance to do any additional operations on output crop file; like quantising PNGs
    // If no optimisations are required then the file was returned unaltered.
    // PNGs are lossless so we can safely follow what the existing pipeline does here.
    val output = mediaType match {
      case Png =>
        val fileName = resizedFile.getAbsolutePath
        val optimisedImageName = fileName.split('.')(0) + "optimised.png"

        logger.info(s"Resaving PNG ${resizedFile.getAbsolutePath} with quantise=true")
        val arena = Arena.ofConfined()
        try {
          val image = VImage.newFromFile(arena, resizedFile.getAbsolutePath)
          val output = new File(optimisedImageName)
          saveImageToFile(image, mediaType, 85, output, quantise = true, keep = Some(VipsRaw.VIPS_FOREIGN_KEEP_XMP))
          arena.close()
          output
        } catch {
          case e: Exception =>
            logger.error(logMarker, "Error while creating thumbnail", e)
            throw e
        } finally {
          arena.close()
        }
      case _ =>
        // no-op for anything other than PNGs
        resizedFile
    }
    output
  }

  def createThumbnail(browserViewableImage: BrowserViewableImage,
                      width: Int,
                      qual: Double = 100d,
                      outputFile: File,
                      iccColourSpace: Option[String],
                      colourModel: Option[String],
                      orientationMetadata: Option[OrientationMetadata]
                     )(implicit logMarker: LogMarker): Future[(File, MimeType)] = {
    Future {
      val arena = Arena.ofConfined()
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
        }.getOrElse{
          inMemoryCopy
        }
        logger.info("Created thumbnail: " + rotated.getWidth + "x" + rotated.getHeight)

        saveImageToFile(rotated, ImageOperations.thumbMimeType, qual, outputFile)
        (outputFile, thumbMimeType)
      } catch {
        case e: Exception =>
          logger.error(logMarker, "Error while creating thumbnail", e)
          throw e
      } finally {
        arena.close()
      }
    }
  }

  def transformImage(sourceFile: File, sourceMimeType: Option[MimeType], tempDir: File)(implicit logMarker: LogMarker): Future[(File, MimeType)] = ???

  def identifyColourModel(sourceFile: File, mimeType: MimeType)(implicit logMarker: LogMarker): Future[Option[String]] = {
    Future {
      val arena = Arena.ofConfined()
      try {
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

        val interpretationRawValue = VipsHelper.image_get_interpretation(image.getUnsafeStructAddress)
        // TODO better way to go straight from int to enum?
        val maybeInterpretation = VipsInterpretation.values().toSeq.find(_.getRawValue == interpretationRawValue)
        maybeInterpretation match {
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
          logger.error(logMarker, "Error during identifyColourModel", e)
          throw e
      } finally {
        arena.close()
      }
    }
  }

  def getColorModelInformation(sourceFile: File)(implicit logMarker: LogMarker): Future[Map[String, String]] = {
    Future {
      implicit val arena: Arena = Arena.ofConfined()
      try {
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
        Map {
          "hasAlpha" -> hasAlpha(image).toString
        }
      } catch {
        case e: Exception =>
          logger.error(logMarker, "Error during getColorModelInformation", e)
          throw e
      } finally {
        arena.close()
      }
    }
  }

  def dimensions(sourceFile: File)(implicit logMarker: LogMarker): Future[Option[Dimensions]] = {
    Future {
      val arena = Arena.ofConfined()
      try {
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
        val width = image.getWidth
        val height = image.getHeight
        Some(Dimensions(width = width, height = height))
      } catch {
        case e: Exception =>
          logger.error(logMarker, "Error during dimensions", e)
          throw e
      } finally {
        arena.close()
      }
    }
  }

  def orientation(sourceFile: File): Future[Option[OrientationMetadata]] = {
    Future {
      val arena = Arena.ofConfined()
      try {
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
        val exifOrientation = VipsHelper.image_get_orientation(image.getUnsafeStructAddress)
        val orientation = Some(OrientationMetadata(
          exifOrientation = Some(exifOrientation)
        ))
        Seq(orientation).flatten.find(_.transformsImage())
      } catch {
        case e: Exception =>
          logger.error("Error during orientation", e)
          throw e
      } finally {
        arena.close()
      }
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

  def saveImageToFile(image: VImage, mimeType: MimeType, qual: Double, outputFile: File, quantise: Boolean = false, keep: Option[Int] = None): File = {
    logger.info(s"Saving image as $mimeType to file: " + outputFile.getAbsolutePath)
    val k = keep.getOrElse(VipsRaw.VIPS_FOREIGN_KEEP_NONE)
    mimeType match {
      case Jpeg =>
        image.jpegsave(outputFile.getAbsolutePath,
          VipsOption.Int("Q", qual.toInt),
          VipsOption.Boolean("optimize-coding", true),
          VipsOption.Int("keep", k)
        )
        outputFile

      case Png =>
        // We are allowed to quantise PNG crops but not the master
        if (quantise) {
          image.pngsave(outputFile.getAbsolutePath,
            VipsOption.Boolean("palette", true),
            VipsOption.Int("Q", qual.toInt),
            //VipsOption.Int("effort", 7),
            //VipsOption.Int("compression", 6),
            VipsOption.Int("keep", k)
          )
        } else {
          image.pngsave(outputFile.getAbsolutePath,
            //VipsOption.Int("compression", 6),
            VipsOption.Int("keep", k)
          )
        }
        outputFile

      case _ =>
        logger.error(s"Save to $mimeType is not supported.")
        throw new UnsupportedCropOutputTypeException
    }
  }

}

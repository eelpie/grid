package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.enums.{VipsIntent, VipsInterpretation}
import app.photofox.vipsffm.jextract.VipsRaw
import app.photofox.vipsffm._
import com.adobe.internal.xmp.options.SerializeOptions
import com.adobe.internal.xmp.{XMPConst, XMPMetaFactory}
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.imaging.im4jwrapper.ImageMagick.ctx
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model._

import java.io._
import java.lang.foreign.Arena
import java.nio.charset.StandardCharsets
import scala.concurrent.Future


class VipsImageOperations extends GridLogging with ImageOperations {

  Vips.init()
  VipsHelper.cache_set_max(0)

  private val thumbnailBackground: java.util.List[java.lang.Double] = java.util.List.of(0x33.toDouble, 0x33.toDouble, 0x33.toDouble)

  def cropImage(
                 sourceFile: File,
                 bounds: Bounds,
                 tempDir: File,
                 orientationMetadata: Option[OrientationMetadata],
                 metadata: ImageMetadata
               )(implicit logMarker: LogMarker, arena: Arena): Future[VImage] = {

    Future {
      // Read source image
      val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)
      // Orient
      val rotated = orientationMetadata.map(_.orientationCorrection()).map { angle =>
        image.rotate(angle)
      }.getOrElse {
        image
      }
      val cropped = rotated.extractArea(bounds.x, bounds.y, bounds.width, bounds.height)

      // If we saw and ICC profile than we will need to transform
      val correctedForICCProfile = correctForICCProfile(cropped)

      val withMetadata = appendMetadata(correctedForICCProfile, metadata)

      val master = withMetadata
      master
    }
  }

  private def makeXmpBlog(metadata: ImageMetadata): Option[Array[Byte]] = {
    val mappings: Seq[(String, String, String)] = Seq(metadata.byline.map { byline =>
      (XMPConst.NS_DC, "creator", byline)
    },
      metadata.credit.map { credit =>
        (XMPConst.NS_PHOTOSHOP, "Credit", credit)
      },
      metadata.copyright.map { copyright =>
        (XMPConst.NS_DC, "rights", copyright)
      },
      metadata.suppliersReference.map { suppliersReference =>
        (XMPConst.NS_PHOTOSHOP, "TransmissionReference",  suppliersReference)
      }).flatten

    mappings.headOption.map { _ =>
      val xmpMeta = XMPMetaFactory.create()
      mappings.foreach { mapping =>
        xmpMeta.setProperty(mapping._1, mapping._2, mapping._3)
      }

      val serializeOptions = new SerializeOptions()
      serializeOptions.setUseCompactFormat(true)
      serializeOptions.setUseCanonicalFormat(false)
      val xmpXml = XMPMetaFactory.serializeToString(xmpMeta, serializeOptions)
      xmpXml.getBytes(StandardCharsets.UTF_8)
    }
  }

  def appendMetadata(image: VImage, metadata: ImageMetadata)(implicit arena: Arena): VImage = {
    logger.info(s"appendMetadata to image")
    makeXmpBlog(metadata).foreach { xmpBlob =>
      logger.info("Tagging master crop with XMP metadata: " + new String(xmpBlob))
      image.set("xmp-data", VBlob.newFromBytes(arena, xmpBlob))
    }
    image
  }

  def resizeImage(
                   sourceImage: VImage,
                   sourceMimeType: Option[MimeType],
                   dimensions: Dimensions,
                   qual: Double = 100d,
                   tempDir: File,
                   fileType: MimeType,
                   quantise: Boolean = false)(implicit logMarker: LogMarker, arena: Arena): Future[File] = {
    Future {
      val scale = dimensions.width.toDouble / sourceImage.getWidth.toDouble
      val resized = sourceImage.resize(scale)

      val outputFile = createTempFileInTempDir("resize", fileType, tempDir)
      logger.info("Saving resized crop as JPEG tmp file to: " + outputFile.getAbsolutePath)

      saveImageToFile(resized, fileType, qual, outputFile, quantise = quantise, keep = Some(VipsRaw.VIPS_FOREIGN_KEEP_XMP)) // TODO quantise false because it is still optimiseOps's decision
    }
  }

  def createThumbnail(browserViewableImage: BrowserViewableImage,
                      width: Int,
                      qual: Double = 100d,
                      outputFile: File,
                      orientationMetadata: Option[OrientationMetadata]
                     )(implicit logMarker: LogMarker): Future[(File, MimeType, Option[Dimensions])] = {
    Future {
      var thumbDimensions: Option[Dimensions] = None
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
        thumbDimensions = Some(Dimensions(rotated.getWidth, rotated.getHeight))

        val flattened = if (rotated.hasAlpha) {
          rotated.flatten(VipsOption.ArrayDouble("background", thumbnailBackground))
        } else {
          rotated
        }

        saveImageToFile(flattened, ImageOperations.thumbMimeType, qual, outputFile)
        (outputFile, ImageOperations.thumbMimeType, thumbDimensions)
      } catch {
        case e: Exception =>
          logger.error(logMarker, "Error while creating thumbnail", e)
          throw e
      } finally {
        arena.close()
      }
    }
  }

  def transformImage(sourceFile: File, tempDir: File, outputMimeType: MimeType)(implicit logMarker: LogMarker): Future[(File, MimeType)] = {
    Future {
      implicit val arena: Arena = Arena.ofConfined
      try {
        val outputFile = createTempFileInTempDir("transformed", outputMimeType, tempDir)
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

        val correctedForICCProfile = correctForICCProfile(image)

        // TODO quantise decision from the caller
        saveImageToFile(correctedForICCProfile, outputMimeType, 85.0, outputFile, quantise = false)
        (outputFile, outputMimeType)

      } catch {
        case e: Exception =>
          logger.error("Error during transformImage", e)
          throw e
      } finally {
        arena.close()
      }
    }
  }

  def getImageInformation(sourceFile: File)(implicit logMarker: LogMarker): Future[(Option[Dimensions], Option[OrientationMetadata], Option[String], Map[String, String])] = {
    Future {
      val arena = Arena.ofConfined
      try {
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

        val dimensions = Some(Dimensions(width = image.getWidth, height = image.getHeight))

        val exifOrientation = VipsHelper.image_get_orientation(image.getUnsafeStructAddress)
        val orientation = Some(OrientationMetadata(
          exifOrientation = Some(exifOrientation)
        ))
        val maybeExifOrientationWhichTransformsImage = Seq(orientation).flatten.find(_.transformsImage())

        val interpretationRawValue = VipsHelper.image_get_interpretation(image.getUnsafeStructAddress)
        // TODO better way to go straight from int to enum?
        val maybeInterpretation = VipsInterpretation.values().toSeq.find(_.getRawValue == interpretationRawValue)
        val colourModel = maybeInterpretation match {
          case Some(VipsInterpretation.INTERPRETATION_B_W) => Some("Greyscale")
          case Some(VipsInterpretation.INTERPRETATION_CMYK) => Some("CMYK")
          case Some(VipsInterpretation.INTERPRETATION_LAB) => Some("LAB")
          case Some(VipsInterpretation.INTERPRETATION_LABS) => Some("LAB")
          case Some(VipsInterpretation.INTERPRETATION_RGB16) => Some("RGB")
          case Some(VipsInterpretation.INTERPRETATION_sRGB) => Some("RGB")
          case _ => None
        }

        val colourModelInformation = Map {
          "hasAlpha" -> hasAlpha(image)(arena).toString
        }
        (dimensions, maybeExifOrientationWhichTransformsImage, colourModel, colourModelInformation)

      } catch {
        case e: Exception =>
          logger.error("Error during identifyColourModel", e)
          arena.close()
          throw e
      } finally {
        arena.close()
      }
    }
  }

  def hasAlpha(image: VImage)(implicit arena: Arena): Boolean = image.hasAlpha

  def isGraphic(image: VImage)(implicit arena: Arena): Boolean = {
    // Indexed plus alpha would be 2 bands
    val numberOfBands = VipsHelper.image_get_bands(image.getUnsafeStructAddress)
    val format = VipsHelper.image_get_format(image.getUnsafeStructAddress)
    val paletteType = VipsHelper.image_get_typeof(arena, image.getUnsafeStructAddress, "palette")

    val isGraphic = paletteType > 0 || numberOfBands < 3
    logger.info(s" isGraphic = $isGraphic: Number of bands: $numberOfBands, Format: $format, Palette type: $paletteType")
    isGraphic
  }

  def saveImageToFile(image: VImage, mimeType: MimeType, qual: Double, outputFile: File, quantise: Boolean = false, keep: Option[Int] = None): File = {
    logger.info(s"Saving image as $mimeType to file: " + outputFile.getAbsolutePath + s" as ${mimeType.name}")
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
          // In libvips Q sets the quality target for the PNG quantiser. The range is 0–100 and the default is 100.
          // A lower Q lets the quantiser use fewer colours and accept more error. You get a smaller, lossier file with more banding.
          // Magick used the quality parameter as the effort, so the value of 1 supplied by pngCropQuality is inappropriate and we will ignore it
          image.pngsave(outputFile.getAbsolutePath,
            VipsOption.Boolean("palette", true),
            VipsOption.Int("Q", 70),
            VipsOption.Int("bitdepth", 8),
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

  private def correctForICCProfile(image: VImage)(implicit arena: Arena): VImage = {
    val needsICCTransform = VipsHelper.image_get_typeof(arena, image.getUnsafeStructAddress, "icc-profile-data") != 0
    if (needsICCTransform) {
      image.iccTransform("srgb",
        VipsOption.Enum("intent", VipsIntent.INTENT_PERCEPTUAL) // Helps with CMYK; see https://github.com/libvips/libvips/issues/1110
      )
    } else {
      // LAB gets corrupted by a needless icc_transform but we will ignore this and hope it improves upstream
      image.iccTransform("srgb",
        VipsOption.Enum("intent", VipsIntent.INTENT_PERCEPTUAL)
      )
    }
  }

  private def createTempFileInTempDir(prefix: String, fileType: MimeType, tempDir: File): File = {
    File.createTempFile(s"$prefix-", fileType.fileExtension, tempDir)
  }

}

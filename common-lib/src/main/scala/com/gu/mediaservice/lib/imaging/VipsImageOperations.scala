package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.enums.{VipsIntent, VipsInterpretation}
import app.photofox.vipsffm.jextract.VipsRaw
import app.photofox.vipsffm.{VBlob, VImage, VipsHelper, VipsOption}
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
               )(implicit logMarker: LogMarker, arena: Arena): Future[File] = {
    Future {
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
      val correctedForICCProfile = if (needsICCTransform) {
        cropped.iccTransform("srgb",
          VipsOption.Enum("intent", VipsIntent.INTENT_PERCEPTUAL), // Helps with CMYK; see https://github.com/libvips/libvips/issues/1110
        )
      } else {
        // LAB gets corrupted by a needless icc_transform
        cropped
      }

      val master = correctedForICCProfile

      // TODO separate this local file create from the vips master image create
      val outputFile = File.createTempFile(s"crop-", s"${fileType.fileExtension}", tempDir) // TODO function for this
      logger.info("Saving master crop tmp file to: " + outputFile.getAbsolutePath)
      saveImageToFile(master, fileType, qual, outputFile, quantise = false, keep = None)

      outputFile
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

  def appendMetadata(sourceFile: File, metadata: ImageMetadata): Future[File] = {
    Future {
      // appendMetadata as a separate step is a poor fit for libvips
      // and this will force another read / write recompression.
      logger.info(s"appendMetadata to ${sourceFile.getAbsolutePath}")
      val arena = Arena.ofConfined()
      val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

      // cropType is not available on the appendMetadata interface; infer it
      val vipsLoader = Option(image.getString("vips-loader")).getOrElse("")
      logger.info(s"vips-loader for ${sourceFile.getAbsolutePath} was: $vipsLoader")
      val cropType = vipsLoader match {
        case l if l.startsWith("jpegload") => Jpeg
        case l if l.startsWith("pngload") => Png
        case l =>
          logger.warn(s"Unexpected vips-loader '$l' for ${sourceFile.getAbsolutePath}; saving as Jpeg")
          Jpeg
      }

      makeXmpBlog(metadata).foreach { xmpBlob =>
        logger.info("Tagging master crop with XMP metadata: " + new String(xmpBlob))
        image.set("xmp-data", VBlob.newFromBytes(arena, xmpBlob))
      }

      saveImageToFile(image, cropType, 95, sourceFile, keep = Some(VipsRaw.VIPS_FOREIGN_KEEP_XMP))
    }
  }

  def resizeImage(
                   sourceImage: VImage,
                   sourceMimeType: Option[MimeType],
                   dimensions: Dimensions,
                   qual: Double = 100d,
                   tempDir: File,
                   fileType: MimeType
                 )(implicit logMarker: LogMarker, arena: Arena): Future[File] = {
    Future {


        val scale = dimensions.width.toDouble / sourceImage.getWidth.toDouble
        val resized = sourceImage.resize(scale)

        val outputFile = File.createTempFile(s"resize-", s"${fileType.fileExtension}", tempDir) // TODO function for this
        logger.info("Saving resized crop as JPEG tmp file to: " + outputFile.getAbsolutePath)

      saveImageToFile(resized, fileType, qual, outputFile, quantise = false, keep = Some(VipsRaw.VIPS_FOREIGN_KEEP_XMP))  // TODO quantise false because it is still optimiseOps's decision
    }
  }

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
        logger.info("Created thumbnail: " + rotated.getWidth + "x" + rotated.getHeight)
        thumbDimensions = Some(Dimensions(rotated.getWidth, rotated.getHeight))

        saveImageToFile(rotated, ImageOperations.thumbMimeType, qual, outputFile)
        (outputFile, thumbMimeType, thumbDimensions)
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
      val arena = Arena.ofConfined
      try {
        val outputFile = File.createTempFile(s"transformed-", outputMimeType.fileExtension, tempDir)
        val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

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

package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.enums.{VipsIntent, VipsInterpretation}
import app.photofox.vipsffm.jextract.VipsRaw
import app.photofox.vipsffm.{VBlob, VImage, VipsHelper, VipsOption}
import com.adobe.internal.xmp.options.SerializeOptions
import com.adobe.internal.xmp.{XMPConst, XMPMetaFactory}
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.imaging.im4jwrapper.ImageMagick.ctx
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, Stopwatch, addLogMarkers}
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
                     iccColourSpace: Option[String],
                     fileType: MimeType,
                     isTransformedFromSource: Boolean,
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
    master
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
      val arena = Arena.ofConfined()
      val image = VImage.newFromFile(arena, sourceFile.getAbsolutePath)

      makeXmpBlog(metadata).foreach { xmpBlob =>
        logger.info("Tagging master crop with XMP metadata: " + new String(xmpBlob))
        image.set("xmp-data", VBlob.newFromBytes(arena, xmpBlob))
      }

      // TODO png?
      // TODO round trip to disk solely to fit File interface
      saveImageToFile(image, Jpeg, 95, sourceFile, keep = Some(VipsRaw.VIPS_FOREIGN_KEEP_XMP))
    }
  }

  def resizeImage(
                       sourceImage: VImage,
                       sourceMimeType: Option[MimeType],
                       dimensions: Dimensions,
                       qual: Double = 100d,
                       tempDir: File,
                       fileType: MimeType
                     )(implicit logMarker: LogMarker, arena: Arena): File = {

    val scale = dimensions.width.toDouble / sourceImage.getWidth.toDouble
    val resized = sourceImage.resize(scale)

    val outputFile = File.createTempFile(s"resize-", s"${fileType.fileExtension}", tempDir) // TODO function for this
    logger.info("Saving resized crop as JPEG tmp file to: " + outputFile.getAbsolutePath)

    saveImageToFile(resized, fileType, qual, outputFile, quantise = true)
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

        val interpretationRawValue = VipsHelper.image_get_interpretation(image.getUnsafeStructAddress)
        // TODO better way to go straight from int to enum?
        val maybeInterpretation = VipsInterpretation.values().toSeq.find(_.getRawValue == interpretationRawValue)
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
          logger.error("Error during createThumbnail", e)
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
          //VipsOption.Boolean("optimize-scans", true),
          VipsOption.Boolean("optimize-coding", true),
          //VipsOption.Boolean("interlace", true),
          //VipsOption.Boolean("trellis-quant", true),
          // VipsOption.Int("quant-table", 3),
          VipsOption.Boolean("strip", true),
          VipsOption.Int("keep", k)
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
            VipsOption.Boolean("strip", true),
            VipsOption.Int("keep", k)
          )
        } else {
          image.pngsave(outputFile.getAbsolutePath,
            //VipsOption.Int("compression", 6),
            VipsOption.Boolean("strip", true),
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

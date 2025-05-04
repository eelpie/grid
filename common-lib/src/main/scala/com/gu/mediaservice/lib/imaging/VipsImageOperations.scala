package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.{VImage, Vips, VipsOption}
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.imaging.VipsImageOperations.thumbMimeType
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model._

import java.io._
import scala.concurrent.{ExecutionContext, Future}



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

  def createThumbnail(browserViewableImage: BrowserViewableImage,
                      width: Int,
                      qual: Double = 100d,
                      outputFile: File,
                      iccColourSpace: Option[String],
                      colourModel: Option[String],
                      orientationMetadata: Option[OrientationMetadata]
                     )(implicit logMarker: LogMarker): Future[(File, MimeType)] = {
    Future {
      Vips.run { arena =>
        val thumbnail = VImage.thumbnail(arena, browserViewableImage.file.getAbsolutePath, width,
          VipsOption.Boolean("auto-rotate", false),
          VipsOption.String("export-profile", "srgb")
        )
       val rotated = orientationMetadata.map(_.orientationCorrection()).map { angle =>
          logger.info("Rotating thumbnail: " + angle)
          thumbnail.rotate(angle)
        }.getOrElse{
          thumbnail
        }

        saveImageToFile(rotated, qual, outputFile)
      }
      (outputFile, thumbMimeType)
    }
  }

  def transformImage(sourceFile: File, sourceMimeType: Option[MimeType], tempDir: File)(implicit logMarker: LogMarker): Future[(File, MimeType)] = ???

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

  def identifyColourModel(sourceFile: File, mimeType: MimeType)(implicit ec: ExecutionContext, logMarker: LogMarker): Future[Option[String]] = ???

}

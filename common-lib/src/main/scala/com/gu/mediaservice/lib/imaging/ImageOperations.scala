package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.VImage
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.embeddings.EmbeddingSourceImageFormat
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model._

import java.io.File
import java.lang.foreign.Arena
import scala.concurrent.Future

object ImageOperations {
  val thumbMimeType: MimeType = Jpeg
}

trait ImageOperations {

  val thumbMimeType: MimeType = ImageOperations.thumbMimeType

  def appendMetadata(image: VImage, metadata: ImageMetadata)(implicit arena: Arena): VImage

  def createEmbeddingSource(originalImageFile: File,
                            orientationMetadata: Option[OrientationMetadata],
                            embeddingSourceImageFormat: EmbeddingSourceImageFormat,
                            outputFile: File
                           ): Future[File]

  def createThumbnail(browserViewableImage: BrowserViewableImage,
                      width: Int,
                      qual: Double = 100d,
                      outputFile: File,
                      orientationMetadata: Option[OrientationMetadata]
                     )(implicit logMarker: LogMarker): Future[(File, MimeType, Option[Dimensions])]

  def cropImage(
                 sourceFile: File,
                 bounds: Bounds,
                 tempDir: File,
                 orientationMetadata: Option[OrientationMetadata],
                 metadata: ImageMetadata
               )(implicit logMarker: LogMarker, arena: Arena): Future[VImage]

  def resizeImage(
                   sourceFile: VImage,
                   dimensions: Dimensions,
                   qual: Double = 100d,
                   tempDir: File,
                   fileType: MimeType,
                   quantise: Boolean
                 )(implicit logMarker: LogMarker, arena: Arena): Future[File]

  def resize(sourceImage: VImage, dimensions: Dimensions): Future[VImage]

  def transformImage(sourceFile: File, tempDir: File, outputMimeType: MimeType, quantise: Boolean)(implicit logMarker: LogMarker): Future[(File, MimeType)]

  def getImageInformation(sourceFile: File)(implicit logMarker: LogMarker): Future[(Option[Dimensions], Option[OrientationMetadata], Option[String], Map[String, String])]

  def saveImageToFile(image: VImage, mimeType: MimeType, qual: Double, outputFile: File, quantise: Boolean = false, keep: Option[Int] = None): File

  def isGraphic(image: VImage)(implicit arena: Arena): Boolean

  def hasAlpha(image: VImage)(implicit arena: Arena): Boolean
}


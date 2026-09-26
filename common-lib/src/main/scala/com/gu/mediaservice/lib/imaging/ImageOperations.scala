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
  val optimisedMimeType: MimeType = Png
}

trait ImageOperations {

  val thumbMimeType: MimeType = ImageOperations.thumbMimeType
  val optimisedMimeType: MimeType = ImageOperations.optimisedMimeType

  def appendMetadata(image: VImage, metadata: ImageMetadata)(implicit arena: Arena): VImage

  def createThumbnail(browserViewableImage: BrowserViewableImage,
                      width: Int,
                      qual: Double = 100d,
                      outputFile: File,
                      orientationMetadata: Option[OrientationMetadata]
                     )(implicit logMarker: LogMarker): Future[(File, MimeType, Option[Dimensions])]

  // Given the path to an original image write a rendering of it which
  // can be ingested by an embedding prediction end point.
  def createEmbeddingSource(originalImageFile: File,
                            orientationMetadata: Option[OrientationMetadata],
                            embeddingSourceImageFormat: EmbeddingSourceImageFormat,
                            outputFile: File
                           ): Future[File]

  def cropImage(
                 sourceFile: File,
                 bounds: Bounds,
                 metadata: ImageMetadata,
                 orientationMetadata: Option[OrientationMetadata]
               )(implicit logMarker: LogMarker, arena: Arena): VImage

  def optimiseCrop(resizedFile: File, mediaType: MimeType)(implicit logMarker: LogMarker): File

  def resizeImage(
                   sourceImage: VImage,
                   dimensions: Dimensions,
                   quality: Int = 100,
                   outputFile: File,
                   fileType: MimeType
                 )(implicit logMarker: LogMarker, arena: Arena): Future[File]

  def getImageInformation(sourceFile: File)(implicit logMarker: LogMarker): Future[(Option[Dimensions], Option[OrientationMetadata], Option[String], Map[String, String])]

}


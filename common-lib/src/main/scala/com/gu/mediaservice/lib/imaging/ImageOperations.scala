package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.VImage

import java.io.File
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.embeddings.EmbeddingSourceImageFormat
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model._

import java.lang.foreign.Arena
import scala.concurrent.Future

trait ImageOperations {

  //def appendMetadata(sourceFile: File, metadata: ImageMetadata): Future[File]

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

  //ef optimiseImage(resizedFile: File, mediaType: MimeType)(implicit logMarker: LogMarker): File

  def resizeImage(
                   sourceImage: VImage,
                   dimensions: Dimensions,
                   quality: Int = 100,
                   outputFile: File,
                   fileType: MimeType
                 )(implicit logMarker: LogMarker, arena: Arena): Future[File]

}

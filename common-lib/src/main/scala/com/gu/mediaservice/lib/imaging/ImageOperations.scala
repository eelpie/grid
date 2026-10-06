package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.VImage
import com.gu.mediaservice.lib.BrowserViewableImage
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

  def appendMetadata(sourceFile: File, metadata: ImageMetadata): Future[File]

  def createThumbnail(browserViewableImage: BrowserViewableImage,
                      width: Int,
                      qual: Double = 100d,
                      outputFile: File,
                      orientationMetadata: Option[OrientationMetadata]
                     )(implicit logMarker: LogMarker): Future[(File, MimeType, Option[Dimensions])]

  def cropImage(
                 sourceFile: File,
                 bounds: Bounds,
                 orientationMetadata: Option[OrientationMetadata]
               )(implicit logMarker: LogMarker, arena: Arena): VImage

  def optimiseCrop(resizedFile: File, mediaType: MimeType)(implicit logMarker: LogMarker): File

  def resizeImage(
                   sourceFile: VImage,
                   dimensions: Dimensions,
                   qual: Double = 100d,
                   tempDir: File,
                   fileType: MimeType
                 )(implicit logMarker: LogMarker, arena: Arena): File

  def getImageInformation(sourceFile: File)(implicit logMarker: LogMarker): Future[(Option[Dimensions], Option[OrientationMetadata], Option[String], Map[String, String])]

}


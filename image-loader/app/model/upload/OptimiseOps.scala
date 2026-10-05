package model.upload

import com.gu.mediaservice.lib.ImageWrapper
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap, Stopwatch}
import com.gu.mediaservice.model.{FileMetadata, MimeType, Png, Tiff}

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

trait OptimiseOps {
  def toOptimisedFile(file: File, imageWrapper: ImageWrapper, tempDir: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)]
  def shouldOptimise(mimeType: Option[MimeType], fileMetadata: FileMetadata): Boolean
  def optimiseMimeType: MimeType
}

class OptimiseWithPngQuant(imageOperations: ImageOperations) extends OptimiseOps {

  override def optimiseMimeType: MimeType = Png

  def toOptimisedFile(file: File, imageWrapper: ImageWrapper, optimisedFile: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)] = Future {
    val marker = MarkerMap(
      "fileName" -> file.getName
    )

    Stopwatch("toOptimisedFile") {
      (imageOperations.optimiseCrop(imageWrapper.file, imageWrapper.mimeType), ImageOperations.optimisedMimeType)
    }(marker)
  }

  def shouldOptimise(mimeType: Option[MimeType], fileMetadata: FileMetadata): Boolean =
    mimeType match {
      case Some(Png) =>
        fileMetadata.colourModelInformation.get("colorType") match {
          case Some("True Color") => true
          case Some("True Color with Alpha") => true
          case _ => false
        }
      case Some(Tiff) => true // TODO This should be done better, it could be better optimised into a jpeg if there is no transparency.
      case _ => false
    }
}

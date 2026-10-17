package model.upload

import com.gu.mediaservice.lib.ImageWrapper
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, MarkerMap, Stopwatch}
import com.gu.mediaservice.model.{FileMetadata, MimeType, Png, Tiff}

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

trait OptimiseOps {
  def toOptimisedFile(file: File, imageWrapper: ImageWrapper, tempDir: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)]
  def shouldOptimise(mimeType: MimeType, fileMetadata: FileMetadata): Boolean
}

class OptimiseWithPngQuant(imageOperations: ImageOperations) extends OptimiseOps with GridLogging {

  def toOptimisedFile(file: File, imageWrapper: ImageWrapper, tempDir: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)] = {

    val marker = MarkerMap(
      "fileName" -> file.getName
    )

    Stopwatch("toOptimisedFile") {
      imageOperations.transformImage(imageWrapper.file, tempDir, ImageOperations.optimisedMimeType)
    }(marker)
  }

  def shouldOptimise(mimeType: MimeType, fileMetadata: FileMetadata): Boolean = {
    // Decide which source images we feel will be slow to render in the UI.
    // These are the ones we will generate an optimised image for.
    val isTiff = mimeType == Tiff

    val isTrueColour = fileMetadata.colourModelInformation.get("colorType") match {
      case Some("True Color") => true
      case Some("True Color with Alpha") => true
      case _ => false
    }

    val isTrueColourPng = isTrueColour && mimeType == Png

    isTiff || isTrueColourPng
  }
}

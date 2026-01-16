package model.upload

import app.photofox.vipsffm.VImage
import com.gu.mediaservice.lib.ImageWrapper
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap}
import com.gu.mediaservice.model.{MimeType, Png}

import java.io.File
import java.lang.foreign.Arena
import scala.concurrent.{ExecutionContext, Future}

trait OptimiseOps {
  def toOptimisedFile(file: File, imageWrapper: ImageWrapper, tempDir: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)]
  def shouldOptimise(mimeType: Option[MimeType]): Boolean
  def optimiseMimeType: MimeType
}

class OptimiseWithPngQuant(imageOperations: ImageOperations) extends OptimiseOps {

  override def optimiseMimeType: MimeType = Png

  def toOptimisedFile(file: File, imageWrapper: ImageWrapper, optimisedFile: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)] = Future {

    val marker = MarkerMap(
      "fileName" -> file.getName
    )

    // Given a source file on any valid upload type, return a file of the optimised type
    try {
      val arena = Arena.ofConfined

      val image = VImage.newFromFile(arena, file.getAbsolutePath)
      imageOperations.saveImageToFile(image: VImage, optimiseMimeType, 85, optimisedFile) // TODO should quantise?
      (optimisedFile, optimiseMimeType)
    } catch {
      case _: Exception =>
        throw new Exception(s"Failed to optimise PNG file ${file.getAbsolutePath}")
    }
  }

  def shouldOptimise(mimeType: Option[MimeType]): Boolean = false
}

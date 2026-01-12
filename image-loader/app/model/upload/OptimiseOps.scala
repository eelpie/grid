package model.upload

import app.photofox.vipsffm.enums.VipsIntent
import app.photofox.vipsffm.{VImage, VipsHelper, VipsOption}
import com.gu.mediaservice.lib.ImageWrapper
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap, Stopwatch}
import com.gu.mediaservice.model.{MimeType, Png, Tiff}

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
    Stopwatch("toOptimisedFile") {
      try {
        val arena = Arena.ofConfined

        val image = VImage.newFromFile(arena, file.getAbsolutePath)

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

        imageOperations.saveImageToFile(correctedForICCProfile: VImage, optimiseMimeType, 85, optimisedFile, quantise = true)
        (optimisedFile, optimiseMimeType)
      } catch {
        case _: Exception =>
          throw new Exception(s"Failed to optimise PNG file ${file.getAbsolutePath}")
      }
    }(marker)
  }

  def shouldOptimise(mimeType: Option[MimeType]): Boolean = {
    mimeType match {
      case Some(Tiff) => true
      case _ => false
    }
  }
}

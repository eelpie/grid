package model.upload

import app.photofox.vipsffm.{VImage, VipsHelper}
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, MarkerMap, Stopwatch}
import com.gu.mediaservice.lib.{BrowserViewableImage, ImageWrapper}
import com.gu.mediaservice.model._

import java.io.File
import java.lang.foreign.Arena
import scala.concurrent.{ExecutionContext, Future}

trait OptimiseOps {
  def toOptimisedFile(imageWrapper: ImageWrapper, orientationMetadata: Option[OrientationMetadata], optimisedMimeType: MimeType, tempDir: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)]
  def shouldOptimise(browserViewableImage: BrowserViewableImage): Option[MimeType]
}

class OptimiseWithPngQuant(imageOperations: ImageOperations) extends OptimiseOps with GridLogging {

  private val maxOptimisedImageDimension: Int = 3840

  def toOptimisedFile(imageWrapper: ImageWrapper, orientationMetadata: Option[OrientationMetadata], optimisedMimeType: MimeType, tempDir: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)] = {

    val marker = MarkerMap(
      "fileName" -> imageWrapper.file.getName
    )

    val arena = Arena.ofConfined()
    val image = VImage.newFromFile(arena, imageWrapper.file.getAbsolutePath)
    val numberOfBands = VipsHelper.image_get_bands(image.getUnsafeStructAddress)
    arena.close()

    val isTrueColour = numberOfBands >= 3
    val isTrueColourPng = isTrueColour && imageWrapper.mimeType == Png

    Stopwatch("toOptimisedFile") {
      imageOperations.transformImage(imageWrapper.file, orientationMetadata, tempDir, optimisedMimeType, quantise = isTrueColourPng, maxDimension = Some(maxOptimisedImageDimension))
    }(marker)
  }

  def shouldOptimise(browserViewableImage: BrowserViewableImage): Option[MimeType] = {
    // Decide which source images we feel will be slow to render in the UI.
    val y = None
    logger.info(s"shouldOptimise $browserViewableImage: $y")
    y
  }

}

object OptimiseWithPngQuant {
  // Source images larger than this are slow to load in the UI regardless of format
  val LargeFileThresholdBytes: Long = 5L * 1024 * 1024
}

package model.upload

import app.photofox.vipsffm.{VImage, VipsHelper}
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, MarkerMap, Stopwatch}
import com.gu.mediaservice.lib.{BrowserViewableImage, ImageWrapper}
import com.gu.mediaservice.model.{Jpeg, MimeType, Png, Tiff}

import java.io.File
import java.lang.foreign.Arena
import scala.concurrent.{ExecutionContext, Future}

trait OptimiseOps {
  def toOptimisedFile(file: File, imageWrapper: ImageWrapper, tempDir: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)]
  def shouldOptimise(browserViewableImage: BrowserViewableImage): Option[MimeType]
}

class OptimiseWithPngQuant(imageOperations: ImageOperations) extends OptimiseOps with GridLogging {

  def toOptimisedFile(file: File, imageWrapper: ImageWrapper, tempDir: File)
                     (implicit ec: ExecutionContext, logMarker: LogMarker): Future[(File, MimeType)] = {

    val marker = MarkerMap(
      "fileName" -> file.getName
    )

    val arena = Arena.ofConfined()
    val image = VImage.newFromFile(arena, file.getAbsolutePath)
    val numberOfBands = VipsHelper.image_get_bands(image.getUnsafeStructAddress)
    arena.close()

    val isTrueColour = numberOfBands >= 3
    val isTrueColourPng = isTrueColour && imageWrapper.mimeType == Png

    Stopwatch("toOptimisedFile") {
      imageOperations.transformImage(imageWrapper.file, tempDir, ImageOperations.optimisedMimeType, quantise = isTrueColourPng)
    }(marker)
  }

  def shouldOptimise(browserViewableImage: BrowserViewableImage): Option[MimeType] = {
    // Decide which source images we feel will be slow to render in the UI.
    // These are the ones we will generate an optimised image for.
    val mimeType = browserViewableImage.mimeType

    val isTiff = mimeType == Tiff

    val arena = Arena.ofConfined()
    val image = VImage.newFromFile(arena, browserViewableImage.file.getAbsolutePath)
    val numberOfBands = VipsHelper.image_get_bands(image.getUnsafeStructAddress)
    val hasAlpha = imageOperations.hasAlpha(image)(arena)
    val isTrueColour = numberOfBands >= 3
    arena.close()

    val x = if (mimeType == Png) {
      if (isTrueColour && !hasAlpha) {
        Some(Jpeg)
      } else {
        None
      }
    } else if (mimeType == Tiff) {
      if (isTrueColour && !hasAlpha) {
        Some(Jpeg)
      } else {
        Some(Png)
      }
    } else {
      None
    }
    logger.info(s"shouldOptimise $browserViewableImage: $x")
    x
  }

}

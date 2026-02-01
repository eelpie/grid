package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.{VImage, Vips}
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap}
import com.gu.mediaservice.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Span}

import java.io.File
import java.lang.foreign.Arena
import scala.concurrent.ExecutionContext.Implicits.global

// This test is disabled for now as it doesn't run on our CI environment, because GraphicsMagick is not present...
class ImageOperationsTest extends AnyFunSpec with Matchers with ScalaFutures {

  Vips.init()

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(1000, Millis), interval = Span(25, Millis))
  implicit val logMarker: LogMarker = MarkerMap()

  private val metadata = ImageMetadata(
    credit = Some("Tony McCrae"),
    copyright = Some("Eel Pie Consulting Ltd"),
    suppliersReference = Some("eelpie-123")
  )

  describe("thumbnail") {
    it("should write thumbnail to output file") {
      val image = fileAt("IMG_4403.jpg")

      val outputFile = new File("/Users/tony/Desktop/thumbnail.jpg")
      val browserViewableImageImage = BrowserViewableImage("TODO", image, Tiff, Map.empty, false,  Instance("TODO"))

      val eventualThumbnail = new ImageOperations("").createThumbnailVips(browserViewableImageImage, 240, 95, outputFile, None)
      whenReady(eventualThumbnail) { r =>
        r._1.isFile should be(true)
      }
    }

    it("render LAB colour spaces correctly in sRGB") {
      val image = fileAt("halfdome_LAB.tif")

      val outputFile = new File("/Users/tony/Desktop/out2.jpg")
      val browserViewableImageImage = BrowserViewableImage("TODO", image, Tiff, Map.empty, false, Instance("TODO"))

      val eventualThumbnail = new ImageOperations("").createThumbnailVips(browserViewableImageImage, 1000, 95, outputFile, None)
      whenReady(eventualThumbnail) { r =>
        r._1.isFile should be(true)
      }
    }

    it("render LAB 16 bits colour spaces correctly in 8 bit sRGB") {
      val image = fileAt("halfdome_LAB16.tif")

      val outputFile = new File("/Users/tony/Desktop/out3.jpg")
      val browserViewableImageImage = BrowserViewableImage("TODO", image, Tiff, Map.empty, false, Instance("TODO"))

      val eventualThumbnail = new ImageOperations("").createThumbnailVips(browserViewableImageImage, 1000, 95, outputFile, None)
      whenReady(eventualThumbnail) { r =>
        r._1.isFile should be(true)
      }
    }

    it("render PNG with alpha correctly") {
      val image = fileAt("with-alpha.png")

      val outputFile = new File("/Users/tony/Desktop/thumbnail-png-with-alpha.jpg")
      val browserViewableImageImage = BrowserViewableImage("TODO", image, Tiff, Map.empty, false, Instance("TODO"))

      val eventualThumbnail = new ImageOperations("").createThumbnailVips(browserViewableImageImage, 1000, 95, outputFile, None)
      whenReady(eventualThumbnail) { r =>
        r._1.isFile should be(true)
      }
    }

    it("render TIF with alpha correctly") {
      val image = fileAt("with-alpha.tif")

      val outputFile = new File("/Users/tony/Desktop/thumbnail-tif-with-alpha.jpg")
      val browserViewableImageImage = BrowserViewableImage("TODO", image, Tiff, Map.empty, false, Instance("TODO"))

      val eventualThumbnail = new ImageOperations("").createThumbnailVips(browserViewableImageImage, 1000, 95, outputFile, None)
      whenReady(eventualThumbnail) { r =>
        r._1.isFile should be(true)
      }
    }
  }

  describe("resize") {
    it("should output resized image to file in chosen format") {
      implicit val arena: Arena = Arena.ofShared()
      val fullSizedImage = VImage.newFromFile(arena, fileAt("IMG_4403.jpg").getAbsolutePath)
      val imageOperations = new ImageOperations("")

      val outputFile = new File("/Users/tony/Desktop/out5.jpg")

      val resized = imageOperations.resizeImageVips(fullSizedImage, Dimensions(1000, 800), 95, outputFile, Jpeg)

      arena.close()
      resized.isFile should be(true)
    }

    it("render LAB colour spaces correctly in sRGB") {
      implicit val arena: Arena = Arena.ofShared
      val imageOperations = new ImageOperations("")

      val fullSizedImage = VImage.newFromFile(arena, fileAt("halfdome_LAB.tif").getAbsolutePath)
      val outputFile = new File("/Users/tony/Desktop/out6.jpg")

      val resized = imageOperations.resizeImageVips(fullSizedImage, Dimensions(800, 600), 95, outputFile, Jpeg)

      arena.close()
      resized.isFile should be(true)
    }

    it("render LAB colour spaces correctly as PNG") {
      implicit val arena: Arena = Arena.ofShared
      val imageOperations = new ImageOperations("")

      val fullSizedImage = VImage.newFromFile(arena, fileAt("halfdome_LAB.tif").getAbsolutePath)
      val outputFile = new File("/Users/tony/Desktop/out7.png")

      val resized = imageOperations.resizeImageVips(fullSizedImage, Dimensions(800, 600), 95, outputFile, Png)

      arena.close()
      resized.isFile should be(true)
    }

    it("render LAB 16 bit colour spaces correctly") {
      implicit val arena: Arena = Arena.ofShared
      val imageOperations = new ImageOperations("")

      val fullSizedImage = VImage.newFromFile(arena, fileAt("halfdome_LAB16.tif").getAbsolutePath)
      val outputFile = new File("/Users/tony/Desktop/out8.jpg")

      val resized = imageOperations.resizeImageVips(fullSizedImage, Dimensions(800, 600), 95, outputFile, Jpeg)

      arena.close()
      resized.isFile should be(true)
    }

    it("render PNG with alpha correctly") {
      implicit val arena: Arena = Arena.ofShared
      val imageOperations = new ImageOperations("")

      val image = fileAt("with-alpha.png")
      val fullSizedImage = VImage.newFromFile(arena, image.getAbsolutePath)
      val outputFile = new File("/Users/tony/Desktop/resized-png-with-alpha.png")

      val resized = imageOperations.resizeImageVips(fullSizedImage, Dimensions(800, 600), 95, outputFile, Png)

      arena.close()
      resized.isFile should be(true)
    }

    it("render LAB TIFF with alpha correctly") {
      implicit val arena: Arena = Arena.ofShared
      val imageOperations = new ImageOperations("")

      val image = fileAt("lab8-with-alpha.tif")
      val fullSizedImage = VImage.newFromFile(arena, image.getAbsolutePath)
      val outputFile = new File("/Users/tony/Desktop/out13.jpg")

    val resized = imageOperations.resizeImageVips(fullSizedImage, Dimensions(800, 600), 95, outputFile, Jpeg)

      arena.close()
      resized.isFile should be(true)
    }
  }

  describe("alpha") {
    it("should return false for RGB for a Jpeg with no alpha") {
      implicit val arena: Arena = Arena.ofShared
      val image =  VImage.newFromFile(arena, fileAt("rgb-wo-profile.jpg").getAbsolutePath)
      val hasAlpha = ImageOperations.hasAlpha(image)
      arena.close()
      hasAlpha should be(false)
    }

    it("should return true for PNG with alpha") {
      implicit val arena: Arena = Arena.ofShared
      val image = VImage.newFromFile(arena, fileAt("with-alpha.png").getAbsolutePath)
      val hasAlpha = ImageOperations.hasAlpha(image)
      arena.close()
      hasAlpha should be(true)
    }
  }

  describe("identifyColourModel") {
    it("should return RGB for a JPG image with RGB image data and no embedded profile") {
      val image = fileAt("rgb-wo-profile.jpg")
      val colourModelFuture = ImageOperations.getImageInformation(image)
      whenReady(colourModelFuture) { colourModel =>
        colourModel._3 should be(Some("RGB"))
      }
    }

    it("should return RGB for a JPG image with RGB image data and an RGB embedded profile") {
      val image = fileAt("rgb-with-rgb-profile.jpg")
      val colourModelFuture = ImageOperations.getImageInformation(image)
      whenReady(colourModelFuture) { colourModel =>
        colourModel._3 should be(Some("RGB"))
      }
    }

    it("should return RGB for a PNG image with RGB image data and an embedded profile") {
      val image = fileAt("cs-black-000.png")
      val colourModelFuture = ImageOperations.getImageInformation(image)
      whenReady(colourModelFuture) { colourModel =>
        colourModel._3 should be(Some("RGB"))
      }
    }

    it("should return RGB for a JPG image with RGB image data and an incorrect CMYK embedded profile") {
      val image = fileAt("rgb-with-cmyk-profile.jpg")
      val colourModelFuture = ImageOperations.getImageInformation(image)
      whenReady(colourModelFuture) { colourModel =>
        colourModel._3 should be(Some("RGB"))
      }
    }

    it("should return CMYK for a JPG image with CMYK image data") {
      val image = fileAt("cmyk.jpg")
      val colourModelFuture = ImageOperations.getImageInformation(image)
      whenReady(colourModelFuture) { colourModel =>
        colourModel._3 should be(Some("CMYK"))
      }
    }

    it("should return Greyscale for a JPG image with greyscale image data and no embedded profile") {
      val image = fileAt("grayscale-wo-profile.jpg")
      val colourModelFuture = ImageOperations.getImageInformation(image)
      whenReady(colourModelFuture) { colourModel =>
        colourModel._3 should be(Some("Greyscale"))
      }
    }

    it("should return RGB for a PNG image with 16 bit RGB image data") {
      val image = fileAt("schaik.com_pngsuite/basi2c16.png")
      val colourModelFuture = ImageOperations.getImageInformation(image)
      whenReady(colourModelFuture) { colourModel =>
        colourModel._3 should be(Some("RGB"))
      }
    }

    it("should return LAB for a TIFF image with LAB16 image data") {
      val image = fileAt("halfdome_LAB16.tif")
      val colourModelFuture = ImageOperations.getImageInformation(image)
      whenReady(colourModelFuture) { colourModel =>
        colourModel._3 should be(Some("LAB"))
      }
    }

    it("should return CMYK for a TIFF image with CMYK image data") {
      val image = fileAt("CMYK-with-profile.jpg")
      val colourModelFuture = ImageOperations.getImageInformation(image)
      whenReady(colourModelFuture) { colourModel =>
        colourModel._3 should be(Some("CMYK"))
      }
    }
  }

  describe("dimensions") {
    it("should return dimensions of horizontal image") {
      val inputFile = fileAt("exif-orientated-no-rotation.jpg")
      val dimsFuture = ImageOperations.getImageInformation(inputFile)
      whenReady(dimsFuture) { dims =>
        dims._1.get shouldBe new Dimensions(3456, 2304)
      }
    }

    it("should return uncorrected dimensions for exif oriented images") {
      val inputFile = fileAt("exif-orientated.jpg")
      val dimsFuture = ImageOperations.getImageInformation(inputFile)
      whenReady(dimsFuture) { dims =>
        dims._1.get shouldBe new Dimensions(3456, 2304)
      }
    }

    it("should read the correct dimensions for a tiff image") {
      val inputFile = fileAt("flower.tif")
      val dimsFuture = ImageOperations.getImageInformation(inputFile)
      whenReady(dimsFuture) { dimOpt =>
        dimOpt._1 should be(Symbol("defined"))
        dimOpt._1.get.width should be(73)
        dimOpt._1.get.height should be(43)
      }
    }

    it("should read the correct dimensions for a png image") {
      val inputFile = fileAt("schaik.com_pngsuite/basn0g08.png")
      val dimsFuture = ImageOperations.getImageInformation(inputFile)
      whenReady(dimsFuture) { dimOpt =>
        dimOpt._1 should be(Symbol("defined"))
        dimOpt._1.get.width should be(32)
        dimOpt._1.get.height should be(32)
      }
    }
  }

  describe("orientation") {
    it("should capture exif orientation tag from JPG images") {
      val image = fileAt("exif-orientated.jpg")
      val orientationFuture = ImageOperations.getImageInformation(image)
      whenReady(orientationFuture) { orientationOpt =>
        orientationOpt._2 should be(defined)
        orientationOpt._2.get.exifOrientation should be(Some(6))
      }
    }

    it("should ignore 0 degree exif orientation tag as it has no material effect") {
      val image = fileAt("exif-orientated-no-rotation.jpg")
      val orientationFuture = ImageOperations.getImageInformation(image)
      whenReady(orientationFuture) { orientationOpt =>
        orientationOpt._2 should be(None)
      }
    }
  }

  describe("graphic detection") {
    it("should return not graphic for true colour jpeg") {
      val arena = Arena.ofConfined
      val image = VImage.newFromFile(arena, fileAt("exif-orientated-no-rotation.jpg").getAbsolutePath)
      ImageOperations.isGraphicVips(image)(arena) should be(false)
      arena.close()
    }

    it("should return is graphic for depth 2 tiff") {
      val arena = Arena.ofConfined
      val image = VImage.newFromFile(arena, fileAt("flower.tif").getAbsolutePath)
      ImageOperations.isGraphicVips(image)(arena) should be(true)
      arena.close()
    }

    it("should return is graphic for depth 4 png with alpha") {
      val arena = Arena.ofConfined
      val image = VImage.newFromFile(arena, fileAt("schaik.com_pngsuite/tbbn0g04.png").getAbsolutePath)
      ImageOperations.isGraphicVips(image)(arena) should be(true)
      arena.close()
    }

    it("should return is graphic for depth 8 indexed png") {
      val arena = Arena.ofConfined
      val image = VImage.newFromFile(arena, fileAt("schaik.com_pngsuite/basn3p08.png").getAbsolutePath)
      ImageOperations.isGraphicVips(image)(arena) should be(true)
      arena.close()
    }

  }

  // TODO: test cropImage and its conversions

  def fileAt(resourcePath: String): File = {
    new File(getClass.getResource(s"/$resourcePath").toURI)
  }

}

package com.gu.mediaservice.lib.imaging

import app.photofox.vipsffm.Vips
import com.gu.mediaservice.lib.BrowserViewableImage
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap}
import com.gu.mediaservice.model.{Instance, Jpeg, Png, Tiff}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Span}

import java.io.File

class VipsImageOperationsTest extends AnyFunSpec with Matchers with ScalaFutures {

  Vips.init()

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(1000, Millis), interval = Span(25, Millis))
  implicit val logMarker: LogMarker = MarkerMap()

  val vipsImageOperations = new VipsImageOperations()

  describe("thumbnail") {
    it("should write thumbnail to output file") {
      val image = fileAt("IMG_4403.jpg")

      val outputFile = File.createTempFile("temp", ".jpg")
      val browserViewableImageImage = BrowserViewableImage("TODO", image, Tiff, Map.empty, isTransformedFromSource = false, Instance("TODO"))

      val eventualThumbnail = vipsImageOperations.createThumbnail(browserViewableImageImage, 500, 85, outputFile, None)
      whenReady(eventualThumbnail) { r =>
        r._1.isFile should be(true)
      }
    }
  }

  describe("identifyColourModel") {
    it("should return RGB for a JPG image with RGB image data and no embedded profile") {
      val image = fileAt("rgb-wo-profile.jpg")
      val colourModelFuture = vipsImageOperations.identifyColourModel(image, Jpeg)
      whenReady(colourModelFuture) { colourModel =>
        colourModel should be (Some("RGB"))
      }
    }

    it("should return RGB for a JPG image with RGB image data and an RGB embedded profile") {
      val image = fileAt("rgb-with-rgb-profile.jpg")
      val colourModelFuture = vipsImageOperations.identifyColourModel(image, Jpeg)
        whenReady(colourModelFuture) { colourModel =>
          colourModel should be(Some("RGB"))
      }
    }

    it("should return RGB for a PNG image with RGB image data and an embedded profile") {
      val image = fileAt("cs-black-000.png")
      val colourModelFuture = vipsImageOperations.identifyColourModel(image, Jpeg)
      whenReady(colourModelFuture) { colourModel =>
        colourModel should be(Some("RGB"))
      }
    }

    it("should return RGB for a JPG image with RGB image data and an incorrect CMYK embedded profile") {
      val image = fileAt("rgb-with-cmyk-profile.jpg")
      val colourModelFuture = vipsImageOperations.identifyColourModel(image, Jpeg)
      whenReady(colourModelFuture) { colourModel =>
        colourModel should be (Some("RGB"))
      }
    }

    it("should return CMYK for a JPG image with CMYK image data") {
      val image = fileAt("cmyk.jpg")
      val colourModelFuture = vipsImageOperations.identifyColourModel(image, Jpeg)
      whenReady(colourModelFuture) { colourModel =>
        colourModel should be (Some("CMYK"))
      }
    }

    it("should return Greyscale for a JPG image with greyscale image data and no embedded profile") {
      val image = fileAt("grayscale-wo-profile.jpg")
      val colourModelFuture = vipsImageOperations.identifyColourModel(image, Jpeg)
      whenReady(colourModelFuture) { colourModel =>
        colourModel should be (Some("Greyscale"))
      }
    }

    it("should return RGB for a PNG image with 16 bit RGB image data") {
      val image = fileAt("schaik.com_pngsuite/basi2c16.png")
      val colourModelFuture = vipsImageOperations.identifyColourModel(image, Png)
      whenReady(colourModelFuture) { colourModel =>
        colourModel should be(Some("RGB"))
      }
    }

    it("should return LAB for a TIFF image with LAB16 image data") {
      val image = fileAt("halfdome_LAB16.tif")
      val colourModelFuture = vipsImageOperations.identifyColourModel(image, Png)
      whenReady(colourModelFuture) { colourModel =>
        colourModel should be(Some("LAB"))
      }
    }

    it("should return CMYK for a TIFF image with CMYK image data") {
      val image = fileAt("CMYK-with-profile.jpg")
      val colourModelFuture = vipsImageOperations.identifyColourModel(image, Png)
      whenReady(colourModelFuture) { colourModel =>
        colourModel should be(Some("CMYK"))
      }
    }
  }

  def fileAt(resourcePath: String): File = {
    new File(getClass.getResource(s"/$resourcePath").toURI)
  }

}

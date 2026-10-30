package lib

import com.gu.mediaservice.lib.aws.{S3, S3Bucket}
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.model._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import java.net.URI

class CropsTest extends AnyFunSpec with Matchers with MockitoSugar {
  import scala.concurrent.ExecutionContext.Implicits.global

  private implicit val instance: Instance = Instance(id = "an-instance")

  it("should return JPEG when the input type is a JPEG") {
    Crops.cropType(Jpeg, isGraphic = false, hasAlpha = false) shouldBe Jpeg
    Crops.cropType(Jpeg, isGraphic = true, hasAlpha = false) shouldBe Jpeg
  }

  it("should return PNG when the input type is PNG and it has alpha") {
    Crops.cropType(Png, isGraphic = true, hasAlpha = true) shouldBe Png
  }

  it("should return PNG when the input type is PNG and it has alpha even if it is True Color") {
    Crops.cropType(Png, isGraphic = false, hasAlpha = true) shouldBe Png
  }

  it("should return PNG when the input type is PNG and it is NOT true color (a graphic)") {
    Crops.cropType(Png, isGraphic = true, hasAlpha = false) shouldBe Png
  }

  it("should return JPEG when the input type is PNG and it is true color") {
    Crops.cropType(Png, isGraphic = false, hasAlpha = false) shouldBe Jpeg
  }

  it("should return PNG when the input type is TIFF and it has alpha") {
    Crops.cropType(Tiff, isGraphic = false, hasAlpha = true) shouldBe Png
  }

  it("should return PNG when the input type is TIFF and it doesn't have alpha or is true color") {
    Crops.cropType(Tiff, isGraphic = true, hasAlpha = false) shouldBe Png
  }

  it("should return JPEG when the input type is TIFF and it doesn't have alpha and it is true color") {
    Crops.cropType(Tiff, isGraphic = false, hasAlpha = false) shouldBe Jpeg
  }

  private val config = mock[CropperConfig]
  private val store = mock[CropStore]
  private val imageOperations: ImageOperations = mock[ImageOperations]
  private val source: SourceImage = SourceImage("test", mock[Asset], valid = true, mock[ImageMetadata], mock[FileMetadata])
  private val bounds: Bounds = Bounds(10, 20, 30, 40)
  private val outputWidth = 1234
  private val imageBucket = S3Bucket("crops-bucket", new URI("https://s3.amazonaws.com"), usesPathStyleURLs = false, client = null, presigner = null)
  private val s3 = new S3(config)

  it("should should construct a correct address for a master jpg") {
    val outputFilename = new Crops(config, store, imageOperations, imageBucket, s3)
      .outputFilename(source, bounds, outputWidth, Jpeg, isMaster = true)
    outputFilename shouldBe "an-instance/test/10_20_30_40/master/1234.jpg"
  }
  it("should should construct a correct address for a non-master jpg") {
    val outputFilename = new Crops(config, store, imageOperations, imageBucket, s3)
      .outputFilename(source, bounds, outputWidth, Jpeg)
    outputFilename shouldBe "an-instance/test/10_20_30_40/1234.jpg"
  }
  it("should should construct a correct address for a non-master tiff") {
    val outputFilename = new Crops(config, store, imageOperations, imageBucket, s3)
      .outputFilename(source, bounds, outputWidth, Tiff)
    outputFilename shouldBe "an-instance/test/10_20_30_40/1234.tiff"
  }
  it("should should construct a correct address for a non-master png") {
    val outputFilename = new Crops(config, store, imageOperations, imageBucket, s3)
      .outputFilename(source, bounds, outputWidth, Png)
    outputFilename shouldBe "an-instance/test/10_20_30_40/1234.png"
  }

  describe("restrictedMasterCropDimensions") {
    val crops = new Crops(config, store, imageOperations, imageBucket, s3)
    val maxMasterCropSize = 4096

    it("should leave dimensions smaller than the max size unchanged") {
      crops.restrictedMasterCropDimensions(Dimensions(2000, 1000), maxMasterCropSize) shouldBe Dimensions(2000, 1000)
    }

    it("should leave dimensions exactly at the max size unchanged") {
      crops.restrictedMasterCropDimensions(Dimensions(4096, 4096), maxMasterCropSize) shouldBe Dimensions(4096, 4096)
      crops.restrictedMasterCropDimensions(Dimensions(4096, 3000), maxMasterCropSize) shouldBe Dimensions(4096, 3000)
    }

    it("should scale an oversized landscape crop so its width is the max size") {
      crops.restrictedMasterCropDimensions(Dimensions(8192, 6144), maxMasterCropSize) shouldBe Dimensions(4096, 3072)
    }

    it("should scale an oversized portrait crop so its height is the max size") {
      crops.restrictedMasterCropDimensions(Dimensions(3000, 6000), maxMasterCropSize) shouldBe Dimensions(2048, 4096)
    }

    it("should scale an oversized square crop to the max size on both sides") {
      crops.restrictedMasterCropDimensions(Dimensions(10000, 10000), maxMasterCropSize) shouldBe Dimensions(4096, 4096)
    }

    it("should use all of the long size") {
      crops.restrictedMasterCropDimensions(Dimensions(6251, 4167), maxMasterCropSize) shouldBe Dimensions(4096, 2730)
    }
  }
}

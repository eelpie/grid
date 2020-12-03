package model

import java.io.File
import java.net.URI
import java.util.UUID

import com.drew.imaging.ImageProcessingException
import com.gu.mediaservice.lib.{StorableOptimisedImage, StorableOriginalImage, StorableThumbImage}
import com.gu.mediaservice.lib.aws.{S3Metadata, S3Object, S3ObjectMetadata}
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.{FileMetadata, Jpeg, MimeType, Png, Tiff, UploadInfo}
import lib.imaging.MimeTypeDetection
import model.upload.{OptimiseWithPngQuant, UploadRequest}
import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Assertion, AsyncFunSuite, Matchers}
import test.lib.ResourceHelpers

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ImageUploadTest extends AsyncFunSuite with Matchers with MockitoSugar {
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  class MockLogMarker extends LogMarker {
    override def markerContents: Map[String, Any] = Map()
  }

  private implicit val logMarker: MockLogMarker = new MockLogMarker()
    // For mime type info, see https://github.com/guardian/grid/pull/2568
    val tempDir = new File("/tmp")
    val mockConfig: ImageUploadOpsCfg = ImageUploadOpsCfg(tempDir, 256, 85d, List(Tiff), "img-bucket", "thumb-bucket")

  /**
    * @todo: I flailed about until I found a path that worked, but
    *        what arcane magic System.getProperty relies upon, and exactly
    *        _how_ it will break in CI, I do not know
    */
  val imageOps: ImageOperations = new ImageOperations(System.getProperty("user.dir"))

  private def imageUpload(
                   fileName: String,
                   expectedOriginalMimeType: MimeType,
                   expectOptimisedFile: Boolean = false): Future[Assertion] = {

    val uuid = UUID.randomUUID()
    val randomId = UUID.randomUUID().toString + fileName

    val mockS3Meta = S3Metadata(Map.empty, S3ObjectMetadata(None, None, None))
    val mockS3Object = S3Object(new URI("innernets.com"), 12345, mockS3Meta)

    def storeOrProjectOriginalFile: StorableOriginalImage => Future[S3Object] = {
      a => {
        Future.successful(
          mockS3Object
            .copy(size = a.file.length())
            .copy(metadata = mockS3Object.metadata
              .copy(objectMetadata = mockS3Object.metadata.objectMetadata
                .copy(contentType = Some(a.mimeType)))))
      }
    }

    def storeOrProjectThumbFile: StorableThumbImage => Future[S3Object] = {
      a => {
        Future.successful(
          mockS3Object
            .copy(size = a.file.length())
            .copy(metadata = mockS3Object.metadata
              .copy(objectMetadata = mockS3Object.metadata.objectMetadata
                .copy(contentType = Some(a.mimeType))))
        )
      }
    }

    def storeOrProjectOptimisedPNG: StorableOptimisedImage => Future[S3Object] = {
      a => {
        if (a.mimeType == Jpeg) fail("We should not create optimised jpg")
        Future.successful(
          mockS3Object
            .copy(size = a.file.length())
            .copy(metadata = mockS3Object.metadata
              .copy(objectMetadata = mockS3Object.metadata.objectMetadata
                .copy(contentType = Some(a.mimeType))))
        )
      }
    }

    val mockDependencies = ImageUploadOpsDependencies(
      mockConfig,
      imageOps,
      storeOrProjectOriginalFile,
      storeOrProjectThumbFile,
      storeOrProjectOptimisedPNG
    )

    val tempFile = ResourceHelpers.fileAt(fileName)
    val ul = UploadInfo(None)

    val uploadRequest = UploadRequest(
      uuid,
      randomId,
      tempFile,
      MimeTypeDetection.guessMimeType(tempFile).right.toOption,
      DateTime.now(),
      "uploadedBy",
      Map(),
      ul
    )

    val futureImage = Uploader.uploadAndStoreImage(
      mockDependencies.storeOrProjectOriginalFile,
      mockDependencies.storeOrProjectThumbFile,
      mockDependencies.storeOrProjectOptimisedImage,
      OptimiseWithPngQuant,
      uploadRequest,
      mockDependencies,
      FileMetadata()
    )

    // Assertions; Failure will auto-fail
    futureImage.map(i => {
      // Assertions on original request
      assert(i.id == randomId, "Correct id comes back")
      assert(i.source.mimeType.contains(expectedOriginalMimeType), "Should have the correct mime type")

      // Assertions on generated thumbnail image
      assert(i.thumbnail.isDefined, "Should always create a thumbnail")
      assert(i.thumbnail.get.mimeType.get == Jpeg, "Should have correct thumb mime type")

      // Assertions on optional generated optimised png image
      assert(i.optimisedPng.isDefined == expectOptimisedFile, "Should have optimised file")
      assert(!expectOptimisedFile || i.optimisedPng.flatMap(p => p.mimeType).contains(Png), "Should have correct optimised mime type")
    })
  }

  test("rubbish") {
    imageUpload("rubbish.jpg", Jpeg)
  }
  test("lighthouse") {
    imageUpload("lighthouse.tif", Tiff, expectOptimisedFile = true)
  }
  test("tiff_8bpc_layered_withTransparency") {
    imageUpload("tiff_8bpc_layered_withTransparency.tif", Tiff, expectOptimisedFile = true)
  }
  test("tiff_8bpc_flat") {
    imageUpload("tiff_8bpc_flat.tif", Tiff, expectOptimisedFile = true)
  }
  test("IndexedColor") {
    imageUpload("IndexedColor.png", Png)
  }
  test("bgan6a16_TrueColorWithAlpha_16bit") {
    imageUpload("bgan6a16_TrueColorWithAlpha_16bit.png", Png, expectOptimisedFile = true)
  }
  test("basn2c16_TrueColor_16bit") {
    imageUpload("basn2c16_TrueColor_16bit.png", Png, expectOptimisedFile = true)
  }
  test("not an image but looks like one") {
    imageUpload("thisisnotanimage.jpg", Png, expectOptimisedFile = true).transformWith{
      case Success(_) => fail("Should have thrown an error")
      case Failure(e) => e match {
        case e: ImageProcessingException => assert(e.getMessage == "File format could not be determined")
      }
    }
  }
  test("not an image and does not look like one") {
    // this exception is thrown before the futures are resolved, and so does not need transformWith
    try {
      imageUpload("thisisnotanimage.stupid", Png, expectOptimisedFile = true)
      fail("Should have thrown an error")
    } catch {
      case e: Exception => assert(e.getMessage == "No idea what you have given me")
    }
  }
}

// todo add to tests - tiff with layers, but not true colour so does not need optimising. MK to provide

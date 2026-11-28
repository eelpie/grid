package com.gu.mediaservice.lib

import com.gu.mediaservice.lib.aws.S3Bucket
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.model.{DeleteObjectRequest, HeadObjectRequest, HeadObjectResponse, NoSuchKeyException}

import java.net.URI

class S3ImageStorageTest extends AnyFunSpec with Matchers with ScalaFutures with MockitoSugar {
  private implicit val marker: LogMarker = MarkerMap()

  private val storage = new S3ImageStorage(mock[CommonConfig])

  private def bucketWith(s3Client: S3Client) =
    S3Bucket("bucket", new URI("https://s3.amazonaws.com"), usesPathStyleURLs = false, s3Client, mock[S3Presigner])

  describe("deleteVersionedImage") {
    it("succeeds without calling deleteObject when the object is already absent") {
      val s3Client = mock[S3Client]
      when(s3Client.headObject(any[HeadObjectRequest])).thenThrow(NoSuchKeyException.builder().build())

      storage.deleteVersionedImage(bucketWith(s3Client), "missing-id").futureValue

      verify(s3Client, never()).deleteObject(any[DeleteObjectRequest])
    }

    it("deletes the specific version returned by headObject") {
      val s3Client = mock[S3Client]
      when(s3Client.headObject(any[HeadObjectRequest]))
        .thenReturn(HeadObjectResponse.builder().versionId("v1").build())

      storage.deleteVersionedImage(bucketWith(s3Client), "present-id").futureValue

      verify(s3Client).deleteObject(DeleteObjectRequest.builder().bucket("bucket").key("present-id").versionId("v1").build())
    }
  }
}

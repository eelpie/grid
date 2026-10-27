package test.lib

import com.gu.mediaservice.lib.aws.S3Bucket

import java.io.File
import java.net.URI

object ResourceHelpers {

  def fileAt(resourcePath: String): File = {
    new File(getClass.getResource(s"/$resourcePath").toURI)
  }

  /** A bucket with no working client/presigner, for tests that never touch S3. */
  def dummyBucket(name: String): S3Bucket =
    S3Bucket(name, new URI("s3.amazonaws.com"), usesPathStyleURLs = false, client = null, presigner = null)

}

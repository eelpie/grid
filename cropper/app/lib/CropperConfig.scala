package lib

import com.gu.mediaservice.lib.aws.S3Bucket
import com.gu.mediaservice.lib.config.{CommonConfig, GridConfigResources}
import com.gu.mediaservice.model.Instance

import java.io.File

class CropperConfig(resources: GridConfigResources) extends CommonConfig(resources) {
  val imageBucket: S3Bucket = S3Bucket(bucket = string("s3.image.bucket.name"),
    endPoint = string("s3.image.bucket.endpoint"),
    usesPathStyleURLs = booleanOpt("s3.image.bucket.pathStyleURLs").getOrElse(false),
    client = s3Client
  )

  val imgPublishingBucket: S3Bucket = S3Bucket(
    string("publishing.image.bucket.name"),
    string("publishing.image.bucket.endpoint"),
    boolean("publishing.image.bucket.pathStyleURLs"),
    s3Client
  )

  val canDownloadCrop: Boolean = boolean("canDownloadCrop")

  val imgPublishingHost = string("publishing.image.host")
  // Note: work around CloudFormation not allowing optional parameters
  val imgPublishingSecureHost = stringOpt("publishing.image.secure.host").filterNot(_.isEmpty)

  val rootUri: Instance => String = services.cropperBaseUri
  val apiUri: Instance => String = services.apiBaseUri

  val tempDir: File = new File(stringDefault("crop.output.tmp.dir", "/tmp"))

  val landscapeCropSizingWidths = List(2000, 1000, 500, 140)
  val portraitCropSizingHeights = List(2000, 1000, 500)
}

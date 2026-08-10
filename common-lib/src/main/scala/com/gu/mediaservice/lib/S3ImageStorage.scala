package com.gu.mediaservice.lib

import com.gu.mediaservice.lib.aws.{S3, S3Bucket}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model.MimeType
import org.slf4j.LoggerFactory

import java.io.File
import scala.concurrent.Future

// TODO: If deleteObject fails - we should be catching the errors here to avoid them bubbling to the application
class S3ImageStorage(config: CommonConfig) extends S3(config) with ImageStorage with GridLogging {

  private val cacheSetting = Some(cacheForever)
  def storeImage(bucket: S3Bucket, id: String, file: File, mimeType: Option[MimeType],
                 meta: Map[String, String] = Map.empty, overwrite: Boolean)
                (implicit logMarker: LogMarker) = {
    logger.info(logMarker, s"bucket: $bucket, id: $id, meta: $meta")
    val eventualObject = if (overwrite) {
      storeV2(bucket, id, file, mimeType, meta, cacheSetting)
    } else {
      storeIfNotPresentV2(bucket, id, file, mimeType, meta, cacheSetting)
    }
    eventualObject.onComplete(o => logger.info(logMarker, s"storeImage completed $o"))
    eventualObject
  }

  def deleteImage(bucket: S3Bucket, id: String)(implicit logMarker: LogMarker) = Future {
    deleteObjectV2(bucket, id)
    logger.info(logMarker, s"Deleted image $id from bucket $bucket")
  }

  def deleteVersionedImage(bucket: S3Bucket, id: String)(implicit logMarker: LogMarker) = Future {
    val objectVersion = getMetadataV2(bucket, id).objectVersion.getOrElse(
      throw new IllegalStateException(s"No version id found for $id in bucket $bucket")
    )
    deleteVersionV2(bucket, id, objectVersion)
    logger.info(logMarker, s"Deleted image $id from bucket $bucket (version: $objectVersion)")
  }

  def deleteFolder(bucket: S3Bucket, id: String)(implicit logMarker: LogMarker): Future[Unit] = listV2(bucket, id).map { files =>
    logger.info(s"Found ${files.size} files to delete in folder $id")
    files.foreach(file => deleteObjectV2(bucket, file.uri.getPath.stripPrefix("/")))
    logger.info(logMarker, s"Deleting images in folder $id from bucket $bucket")
  }

}

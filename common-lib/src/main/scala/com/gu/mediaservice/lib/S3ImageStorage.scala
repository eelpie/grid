package com.gu.mediaservice.lib

import com.gu.mediaservice.lib.aws.{S3, S3Bucket}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model.MimeType
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request

import java.io.File
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

// TODO: If deleteObject fails - we should be catching the errors here to avoid them bubbling to the application
class S3ImageStorage(config: CommonConfig) extends S3(config) with ImageStorage with GridLogging {

  private val cacheSetting = Some(cacheForever)
  def storeImage(bucket: S3Bucket, id: String, file: File, mimeType: Option[MimeType],
                 meta: Map[String, String] = Map.empty, overwrite: Boolean)
                (implicit logMarker: LogMarker) = {
    logger.info(logMarker, s"bucket: ${bucket.name}, id: $id, meta: $meta")
    val eventualObject = if (overwrite) {
      store(bucket, id, file, mimeType, meta, cacheSetting)
    } else {
      storeIfNotPresent(bucket, id, file, mimeType, meta, cacheSetting)
    }
    eventualObject.onComplete(o => logger.info(logMarker, s"storeImage completed $o"))
    eventualObject
  }

  def deleteImage(bucket: S3Bucket, key: String)(implicit logMarker: LogMarker) = Future {
    deleteObject(bucket, key)
    logger.info(logMarker, s"Deleted image $key from bucket ${bucket.name}")
  }

  def deleteVersionedImage(bucket: S3Bucket, id: String)(implicit logMarker: LogMarker) = Future {
    val objectVersion = getMetadata(bucket, id).objectVersion.getOrElse(
      throw new IllegalStateException(s"No version id found for $id in bucket ${bucket.name}")
    )
    deleteVersion(bucket, id, objectVersion)
    logger.info(logMarker, s"Deleted image $id from bucket ${bucket.name} (version: $objectVersion)")
  }

  def deleteFolder(bucket: S3Bucket, id: String)(implicit logMarker: LogMarker): Future[Unit] = list(bucket, id).map { files =>
    logger.info(s"Found ${files.size} files to delete in folder $id")
    files.foreach(file => deleteObject(bucket, file.uri.getPath.stripPrefix("/")))
    logger.info(logMarker, s"Deleting images in folder $id from bucket $bucket")
  }

}

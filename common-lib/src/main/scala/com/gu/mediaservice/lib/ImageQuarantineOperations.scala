package com.gu.mediaservice.lib

import java.io.File
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.aws.{S3Bucket, S3Object}
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.{Instance, MimeType}

import scala.concurrent.Future

class ImageQuarantineOperations(quarantineBucket: S3Bucket, config: CommonConfig, isVersionedS3: Boolean = false)
  extends S3ImageStorage(config) {

  def storeQuarantineImage(id: String, file: File, mimeType: Option[MimeType], meta: Map[String, String] = Map.empty, instance: Instance)
                       (implicit logMarker: LogMarker): Future[S3Object] =
    storeImage(quarantineBucket, ImageIngestOperations.fileKeyFromId(id, instance), file, mimeType, meta, overwrite = true)
}




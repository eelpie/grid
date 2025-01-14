package model

import com.gu.mediaservice.lib.ImageStorageProps
import com.gu.mediaservice.lib.logging.LogMarker
import lib.storage.ImageLoaderStore

import scala.jdk.CollectionConverters._

case class S3IngestObject (
  key: String,
  uploadedBy: String,
  filename:String,
  maybeMediaIdFromUiUpload: Option[String],
  uploadTime: java.util.Date,
  contentLength: Long,
  getInputStream: () => java.io.InputStream,
  identifiers: Map[String, String] = Map.empty,
  isFeedUpload: Boolean,
)

object S3IngestObject {

  def apply (key: String, store: ImageLoaderStore)(implicit logMarker: LogMarker): S3IngestObject  = {

    val keyParts = key.split("/")

    val s3Object = store.getS3Object(key)
    val metadata = s3Object.getObjectMetadata

    val mediaIdFromUiUpload = metadata.getUserMetadata.asScala.get("media-id")
    val isFeedUpload = mediaIdFromUiUpload.isEmpty  // TODO Not concise

    S3IngestObject(
      key,
      uploadedBy = keyParts.head,
      filename = keyParts.last,
      maybeMediaIdFromUiUpload = mediaIdFromUiUpload, // set by the client in upload in manager.js
      uploadTime = metadata.getLastModified,
      contentLength = metadata.getContentLength,
      getInputStream = () => s3Object.getObjectContent,
      identifiers = metadata.getUserMetadata.asScala.collect{
        case (key, value) if key.startsWith(ImageStorageProps.identifierMetadataKeyPrefix) =>
          key.stripPrefix(ImageStorageProps.identifierMetadataKeyPrefix) -> value
      }.toMap,
      isFeedUpload = isFeedUpload
    )
  }
}


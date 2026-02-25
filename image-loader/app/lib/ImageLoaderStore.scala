package lib.storage

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.model.{AmazonS3Exception, GeneratePresignedUrlRequest, S3Object}
import lib.ImageLoaderConfig
import com.gu.mediaservice.lib
import com.gu.mediaservice.lib.aws
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model.Instance

import java.io.File
import java.time.ZonedDateTime
import java.util.Date
import scala.concurrent.Future

class S3FileDoesNotExistException extends Exception()

class ImageLoaderStore(config: ImageLoaderConfig) extends lib.ImageIngestOperations(config.imageBucket, config.thumbnailBucket, config) with GridLogging {

  private val imageIngestS3Endpoint = AmazonAwsS3Endpoint

  private def handleNotFound[T](key: String)(doWork: => T)(loggingIfNotFound: => Unit): T = {
    try {
      doWork
    } catch {
      case e: AmazonS3Exception if e.getStatusCode == 404 || e.getStatusCode == 403 => {
        logger.warn(s"AmazonS3Exception ${e.getStatusCode} for key '$key'")
        loggingIfNotFound
        throw new S3FileDoesNotExistException
      }
      case other: Throwable => throw other
    }
  }

  def getS3Object(key: String)(implicit logMarker: LogMarker): S3Object = handleNotFound(key) {
    getObject(config.maybeIngestBucket.get, key)
  } {
    logger.error(logMarker, s"Attempted to read $key from ingest bucket, but it does not exist.")
  }

  def queueS3Object(uploader: String, filename: String, s3Meta: Map[String, String], file: File)(implicit logMarker: LogMarker, instance: Instance): Future[aws.S3Object] = {
    store(
        config.maybeIngestBucket.get,
        s"${instance.id}/$uploader/$filename",
        file,
        mimeType = None, // we don't care as this is just the queue bucket
        meta = s3Meta,
      )
  }

  def generatePreSignedUploadUrl(filename: String, expiration: ZonedDateTime, uploadedBy: String, mediaId: String)(implicit instance: Instance): String = {
    val request = new GeneratePresignedUrlRequest(
      config.maybeBucketForUIUploads.get.bucket, // bucket
      s"${instance.id}/$uploadedBy/$filename", // key
    )
      .withMethod(HttpMethod.PUT)
      .withExpiration(Date.from(expiration.toInstant));

    // sent by the client in manager.js
    request.putCustomRequestHeader("x-amz-meta-media-id", mediaId)

    generatePresignedRequest(request, config.maybeIngestBucket.get).toString
  }

  def moveObjectToFailedBucket(key: String)(implicit logMarker: LogMarker) = handleNotFound(key){
    copyObject(config.maybeIngestBucket.get, config.maybeFailBucket.get, key) // TODO Naked get - make optional
    deleteObjectFromIngestBucket(key)
  } {
    logger.warn(logMarker, s"Attempted to copy $key from ingest bucket to fail bucket, but it does not exist.")
  }

  def deleteObjectFromIngestBucket(key: String)(implicit logMarker: LogMarker) = handleNotFound(key) {
    deleteObject(config.maybeIngestBucket.get, key)
  } {
    logger.warn(logMarker, s"Attempted to delete $key from ingest bucket, but it does not exist.")
  }
}


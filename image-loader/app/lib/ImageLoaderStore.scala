package lib.storage

import lib.ImageLoaderConfig
import com.gu.mediaservice.lib
import com.gu.mediaservice.lib.aws
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model.Instance
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

import java.io.File
import java.time.Duration
import scala.concurrent.Future
import scala.jdk.CollectionConverters.MapHasAsJava

class S3FileDoesNotExistException extends Exception()

class ImageLoaderStore(config: ImageLoaderConfig) extends lib.ImageIngestOperations(config.imageBucket, config.thumbnailBucket, config.embeddingSourceBucket, config) with GridLogging {

  private def handleNotFound[T](key: String)(doWork: => T)(loggingIfNotFound: => Unit): T = {
    try {
      doWork
    } catch {
      case e: S3Exception if e.statusCode() == 404 || e.statusCode() == 403 => {
        logger.warn(s"AmazonS3Exception ${e.statusCode()} for key '$key'")
        loggingIfNotFound
        throw new S3FileDoesNotExistException
      }
      case other: Throwable => throw other
    }
  }

  def getS3Object(key: String)(implicit logMarker: LogMarker): ResponseInputStream[GetObjectResponse] = handleNotFound(key) {
      getObjectV2(config.maybeIngestBucket.get, key)
  } {
    logger.error(logMarker, s"Attempted to read $key from ingest bucket, but it does not exist.")
  }

  def queueS3Object(uploader: String, filename: String, s3Meta: Map[String, String], file: File)(implicit logMarker: LogMarker, instance: Instance): Future[aws.S3Object] = {
    storeV2(
        config.maybeIngestBucket.get,
        s"${instance.id}/$uploader/$filename",
        file,
        mimeType = None, // we don't care as this is just the queue bucket
        meta = s3Meta,
      )
  }
  val presigner = S3Presigner.create()
  def generatePreSignedUploadUrl(filename: String, duration: Duration, uploadedBy: String, mediaId: String)(implicit instance: Instance): String = {

    val putObjectRequest = PutObjectRequest.builder()
      .bucket(config.maybeBucketForUIUploads.get.bucket).key(s"${instance.id}/$uploadedBy/$filename").metadata(Map(
        "media-id" -> mediaId).asJava)
      .build()
    val putObjectPresignRequest =
      PutObjectPresignRequest.builder()
        .putObjectRequest(putObjectRequest)
        .signatureDuration(duration)
        .build();

    val req = presigner.presignPutObject(putObjectPresignRequest)
    req.url().toExternalForm
  }

  def moveObjectToFailedBucket(key: String)(implicit logMarker: LogMarker): Unit = handleNotFound(key){
    val sourceBucket = config.maybeIngestBucket.get  // TODO Naked get - make optional
    val destinationBucket = config.maybeFailBucket.get // TODO Naked get - make optional
    copyV2(key, sourceBucket, destinationBucket)
    deleteObjectFromIngestBucket(key)
  } {
    logger.warn(logMarker, s"Attempted to copy $key from ingest bucket to fail bucket, but it does not exist.")
  }

  def deleteObjectFromIngestBucket(key: String)(implicit logMarker: LogMarker): Unit = handleNotFound(key) {
    deleteObjectV2(config.maybeIngestBucket.get, key)
  } {
    logger.warn(logMarker, s"Attempted to delete $key from ingest bucket, but it does not exist.")
  }

}


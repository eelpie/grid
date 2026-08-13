package com.gu.mediaservice.lib.aws

import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, Stopwatch}
import com.gu.mediaservice.model._
import org.joda.time.{DateTime, DateTimeZone}
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.{GetObjectPresignRequest, PresignedPutObjectRequest, PutObjectPresignRequest}
import software.amazon.awssdk.services.s3.{S3Client, S3Configuration}

import java.io.File
import java.net.{URI, URL}
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

case class S3Object(uri: URI, size: Long, metadata: S3Metadata)

object S3Object {

  def apply(bucket: S3Bucket, key: String, size: Long, metadata: S3Metadata): S3Object =
    apply(bucket.objectUrl(key), size, metadata)

  def apply(bucket: S3Bucket, key: String, file: File, mimeType: Option[MimeType], lastModified: Option[DateTime],
            meta: Map[String, String] = Map.empty, cacheControl: Option[String] = None): S3Object = {
    S3Object(
      bucket,
      key,
      file.length,
      S3Metadata(
        meta,
        S3ObjectMetadata(
          mimeType,
          cacheControl,
          lastModified
        )
      )
    )
  }
}

case class S3Metadata(userMetadata: Map[String, String], objectMetadata: S3ObjectMetadata, objectVersion: Option[String] = None)

object S3Metadata {
  def apply(meta: HeadObjectResponse): S3Metadata = {
    S3Metadata(
      meta.metadata().asScala.toMap,
      S3ObjectMetadata(
        contentType = Option(meta.contentType()).filterNot(_.toLowerCase == "application/octet-stream").map(MimeType.apply),
        cacheControl = Option(meta.cacheControl()),
        lastModified = Option(meta.lastModified()).map(l => new DateTime(l.toEpochMilli).withZone(DateTimeZone.UTC))
      ),
      objectVersion = Option(meta.versionId())
    )
  }
}

case class S3ObjectMetadata(contentType: Option[MimeType], cacheControl: Option[String], lastModified: Option[DateTime])

class S3(config: CommonConfig) extends GridLogging with ContentDisposition with RoundedExpiration {
  type Key = String
  type UserMetadata = Map[String, String]

  def signUrl(
                 bucket: S3Bucket,
                 url: URI,
                 image: Image,
                 expiration: DateTime = cachableExpiration(),
                 imageType: ImageFileType = Source
               ): String = {
    val key: Key = bucket.keyFromURL(url)

    val contentDisposition = getContentDisposition(image, imageType, config.shortenDownloadFilename)

    val nowMillis = System.currentTimeMillis()
    val targetExpirationMillis = expiration.getMillis
    val remainingSeconds = Math.max(1, (targetExpirationMillis - nowMillis) / 1000)

    val getObjectRequest = GetObjectRequest.builder()
      .bucket(bucket.name)
      .key(key)
      .responseContentDisposition(contentDisposition)
      .build()

    val getObjectPresignRequest = GetObjectPresignRequest.builder()
      .getObjectRequest(getObjectRequest)
      .signatureDuration(Duration.ofSeconds(remainingSeconds))
      .build()

    val req = bucket.presigner.presignGetObject(getObjectPresignRequest)
    req.url().toExternalForm
  }

  def signUrlTony(bucket: S3Bucket, url: URI, expiration: DateTime = cachableExpiration()): URL = {
    val key: Key = bucket.keyFromURL(url)

    val nowMillis = System.currentTimeMillis()
    val targetExpirationMillis = expiration.getMillis
    val remainingSeconds = Math.max(1, (targetExpirationMillis - nowMillis) / 1000)

    val getObjectRequest = GetObjectRequest.builder()
      .bucket(bucket.name)
      .key(key)
      .build()

    val getObjectPresignRequest = GetObjectPresignRequest.builder()
      .getObjectRequest(getObjectRequest)
      .signatureDuration(Duration.ofSeconds(remainingSeconds))
      .build()

    val req = bucket.presigner.presignGetObject(getObjectPresignRequest)
    req.url()
  }

  def presignPutObject(bucket: S3Bucket, putObjectPresignRequest: PutObjectPresignRequest): PresignedPutObjectRequest = {
    bucket.presigner.presignPutObject(putObjectPresignRequest)
  }

  def getObject(bucket: S3Bucket, key: String): ResponseInputStream[GetObjectResponse] = {
    bucket.client.getObject(GetObjectRequest.builder().key(key).bucket(bucket.name).build())
  }

  def getObjectAsString(bucket: S3Bucket, key: String): Option[String] = {
    try {
      val stream = bucket.client.getObject(GetObjectRequest.builder().key(key).bucket(bucket.name).build());
      Some(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
    } catch {
      case e: NoSuchKeyException =>
        logger.warn(s"Cannot find key: $key in bucket: ${bucket.name}")
        None
    }
  }

  def putString(bucket: S3Bucket, key: String, fileContents: String) = {
    bucket.client.putObject(PutObjectRequest.builder().bucket(bucket.name).key(key).build(), RequestBody.fromString(fileContents))
  }

  def store(bucket: S3Bucket, id: Key, file: File, mimeType: Option[MimeType], meta: UserMetadata = Map.empty, cacheControl: Option[String] = None)
           (implicit ex: ExecutionContext, logMarker: LogMarker): Future[S3Object] =
    Future {

      val fileMarkers = Map(
        "bucket" -> bucket.name,
      )
      val markers = logMarker ++ fileMarkers

      val reqBuilder = PutObjectRequest.builder().key(id).bucket(bucket.name)
      cacheControl.foreach(c => reqBuilder.cacheControl(c))
      mimeType.foreach(m => reqBuilder.contentType(m.name))
      reqBuilder.metadata(meta.asJava)
      val req = reqBuilder.build()

      Stopwatch(s"S3 client.putObject ($req)"){
        bucket.client.putObject(req, RequestBody.fromFile(file))
        // once we've completed the PUT read back to ensure that we are returning reality
        val metadata = bucket.client.headObject(
          HeadObjectRequest.builder().key(id).bucket(bucket.name).build()
        )

        S3Object(bucket, id, metadata.contentLength(), S3Metadata(metadata))
      }(markers)
    }

  def storeIfNotPresent(bucket: S3Bucket, id: Key, file: File, mimeType: Option[MimeType], meta: UserMetadata = Map.empty, cacheControl: Option[String] = None)
                       (implicit ex: ExecutionContext, logMarker: LogMarker): Future[S3Object] = {
    Future {
      Some(bucket.client.headObject(
        HeadObjectRequest.builder().key(id).bucket(bucket.name).build()
      ))
    }.recover {
      // translate this exception into the object not existing
      case _: NoSuchKeyException => None
    }.flatMap {
      case Some(metadata) =>
        logger.info(logMarker, s"Skipping storing of S3 file $id as key is already present in bucket ${bucket.name}")
        Future.successful(S3Object(bucket, id, metadata.contentLength(), S3Metadata(metadata)))
      case None =>
        store(bucket, id, file, mimeType, meta, cacheControl)
    }
  }

  def list(bucket: S3Bucket, prefixDir: String)
          (implicit ex: ExecutionContext): Future[List[S3Object]] =
    Future {
      val req = ListObjectsV2Request.builder().bucket(bucket.name).prefix(s"$prefixDir/").build()
      val listing = bucket.client.listObjectsV2(req)
      val s3Objects = listing.contents().asScala.toList
      s3Objects.map(s3Object => {
        S3Object(bucket, s3Object.key(), size = s3Object.size(), metadata = getMetadata(bucket, s3Object.key()))
      })
    }

  def getMetadata(bucket: S3Bucket, key: Key): S3Metadata = {
    val meta = bucket.client.headObject(HeadObjectRequest.builder().key(key).bucket(bucket.name).build())
    S3Metadata(meta)
  }

  def syncFindKey(bucket: S3Bucket, prefixName: String): Option[Key] = {
    val req = ListObjectsV2Request.builder().bucket(bucket.name).prefix(s"$prefixName-").build()
    val objects = bucket.client.listObjectsV2(req).contents().asScala.toList
    objects.headOption.map(_.key())
  }
  def doesObjectExist(bucket: S3Bucket, key: String) = {
    try {
      bucket.client.headObject(
        HeadObjectRequest.builder().key(key).bucket(bucket.name).build()
      )
      true
    } catch {
      case _: NoSuchKeyException => false
    }
  }

  def deleteObject(bucket: S3Bucket, key: String): Unit =
    bucket.client.deleteObject(DeleteObjectRequest.builder().bucket(bucket.name).key(key).build())

  def deleteObjects(bucket: S3Bucket, keys: List[String]): Map[String, Boolean] = {
    val objects: util.List[ObjectIdentifier] = keys.map { key =>
      ObjectIdentifier.builder()
        .key(key)
        .build()
    }.asJava
    val response = bucket.client.deleteObjects(
      DeleteObjectsRequest.builder().bucket(bucket.name)
        .delete(Delete.builder().objects(objects).build())
        .build()
    )
    val errorKeys = response.errors().asScala.toList.map(_.key())
    keys.map { key =>
      key -> !errorKeys.contains(key)
    }.toMap
  }

  def deleteVersion(bucket: S3Bucket, key: String, objectVersion: String): Unit =
    bucket.client.deleteObject(DeleteObjectRequest.builder().bucket(bucket.name).key(key).versionId(objectVersion).build())

  def copy(key: String, sourceBucket: S3Bucket, destinationBucket: S3Bucket): CopyObjectResponse = {
    sourceBucket.client.copyObject(
      CopyObjectRequest.builder()
        .sourceBucket(sourceBucket.name)
        .sourceKey(key)
        .destinationBucket(destinationBucket.name)
        .destinationKey(key)
        .build()
    )
  }

}

object S3Ops extends GridLogging {
  // TODO make this localstack friendly
  // TODO: Make this region aware - i.e. RegionUtils.getRegion(region).getServiceEndpoint(AmazonS3.ENDPOINT_PREFIX)
  val s3Endpoint = "s3.amazonaws.com"

  def buildS3Client(config: CommonConfig, endpointOverride: Option[String] = None, usesPathStyleURLs: Boolean = false, maybeRegionOverride: Option[Region] = None): S3Client = {
    val builder = S3Client.builder()
      .credentialsProvider(config.awsCredentials)
      .region(maybeRegionOverride.getOrElse(config.awsRegion))
      .forcePathStyle(usesPathStyleURLs)

    val withEndpoint = endpointOverride match {
      case Some(endpoint) =>
        logger.info(s"creating S3 client with endpoint override: $endpoint")
        builder.endpointOverride(new URI(endpoint))
      case _ => builder
    }

    withEndpoint.build()
  }

  def buildPresignerClientV2(config: CommonConfig, endpointOverride: Option[String] = None, usesPathStyleURLs: Boolean = false, maybeRegionOverride: Option[Region] = None): S3Presigner = {
    val builder = S3Presigner.builder()
      .credentialsProvider(config.awsCredentials)
      .region(maybeRegionOverride.getOrElse(config.awsRegion))
      .serviceConfiguration(S3Configuration.builder()
        .pathStyleAccessEnabled(usesPathStyleURLs)
        .build())

    val withEndpoint = endpointOverride match {
      case Some(endpoint) =>
        logger.info(s"creating S3 presigner with endpoint override: $endpoint")
        builder.endpointOverride(new URI(endpoint))
      case _ => builder
    }

    withEndpoint.build()
  }

}

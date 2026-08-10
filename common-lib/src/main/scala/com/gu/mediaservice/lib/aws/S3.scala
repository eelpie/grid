package com.gu.mediaservice.lib.aws

import com.amazonaws.services.s3.model.{Region => _, _}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker, Stopwatch}
import com.gu.mediaservice.model._
import org.joda.time.{DateTime, DateTimeZone}
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, CopyObjectResponse, Delete, DeleteObjectRequest, DeleteObjectsRequest, GetObjectResponse, HeadObjectRequest, HeadObjectResponse, ListObjectsV2Request, NoSuchKeyException, ObjectIdentifier, PutObjectRequest, GetObjectRequest => GetObjectRequestV2, PutObjectRequest => PutObjectRequestV2}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

import java.io.File
import java.net.{URI, URL}
import java.nio.charset.StandardCharsets
import java.time.{Duration => JavaDuration}
import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

case class S3Object(uri: URI, size: Long, metadata: S3Metadata)

object S3Object {
  def objectUrl(bucket: String, key: String): URI = {
    val bucketUrl = s"$bucket.${S3Ops.s3Endpoint}"
    new URI("http", bucketUrl, s"/$key", null)
  }

  def apply(bucket: String, key: String, size: Long, metadata: S3Metadata): S3Object =
    apply(objectUrl(bucket, key), size, metadata)

  def apply(bucket: String, key: String, file: File, mimeType: Option[MimeType], lastModified: Option[DateTime],
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
  def apply(meta: ObjectMetadata): S3Metadata = {
    S3Metadata(
      meta.getUserMetadata.asScala.toMap,
      S3ObjectMetadata(
        contentType = Option(meta.getContentType).filterNot(_.toLowerCase == "application/octet-stream").map(MimeType.apply),
        cacheControl = Option(meta.getCacheControl),
        lastModified = Option(meta.getLastModified).map(new DateTime(_))
      ),
      objectVersion = Option(meta.getVersionId)
    )
  }
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
  type Bucket = String
  type Key = String
  type UserMetadata = Map[String, String]

  private lazy val clientV2: S3Client = S3Ops.buildS3ClientV2(config)

  private def signatureDuration(expiration: DateTime): JavaDuration =
    JavaDuration.ofMillis(expiration.getMillis - DateTime.now().getMillis)

  def signUrl(bucket: Bucket, url: URI, image: Image, expiration: DateTime = cachableExpiration(), imageType: ImageFileType = Source): String = {
    // get path and remove leading `/`
    val key: Key = url.getPath.drop(1)

    val contentDisposition = getContentDisposition(image, imageType, config.shortenDownloadFilename)

    val getObjectRequest = GetObjectRequestV2.builder().bucket(bucket).key(key).responseContentDisposition(contentDisposition).build()
    val presignRequest = GetObjectPresignRequest.builder().signatureDuration(signatureDuration(expiration)).getObjectRequest(getObjectRequest).build()

    S3Ops.buildS3PresignerV2(config).presignGetObject(presignRequest).url().toExternalForm
  }

  def signUrlTony(bucket: Bucket, url: URI, expiration: DateTime = cachableExpiration()): URL = {
    // get path and remove leading `/`
    val key: Key = url.getPath.drop(1)

    val getObjectRequest = GetObjectRequestV2.builder().bucket(bucket).key(key).build()
    val presignRequest = GetObjectPresignRequest.builder().signatureDuration(signatureDuration(expiration)).getObjectRequest(getObjectRequest).build()

    S3Ops.buildS3PresignerV2(config).presignGetObject(presignRequest).url()
  }

  def getObjectV2(bucket: Bucket, url: URI): ResponseInputStream[GetObjectResponse]= {
    // get path and remove leading `/`
    val key: Key = url.getPath.drop(1)
    clientV2.getObject(GetObjectRequestV2.builder().key(key).bucket(bucket).build())
  }

  def getObjectV2(bucket: Bucket, key: String): ResponseInputStream[GetObjectResponse] = {
    clientV2.getObject(GetObjectRequestV2.builder().key(key).bucket(bucket).build())
  }

  def getObjectAsStringV2(bucket: Bucket, key: String): Option[String] = {
    try {
      val stream = clientV2.getObject(GetObjectRequestV2.builder().key(key).bucket(bucket).build());
      Some(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
    } catch {
      case e: NoSuchKeyException =>
        logger.warn(s"Cannot find key: $key in bucket: $bucket")
        None
    }
  }

  def putString(bucket: String, key: String, fileContents: String) = {
    clientV2.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromString(fileContents))
  }

  def storeV2(bucket: Bucket, id: Key, file: File, mimeType: Option[MimeType], meta: UserMetadata = Map.empty, cacheControl: Option[String] = None)
           (implicit ex: ExecutionContext, logMarker: LogMarker): Future[S3Object] =
    Future {

      val fileMarkers = Map(
        "bucket" -> bucket,
      )
      val markers = logMarker ++ fileMarkers

      val reqBuilder = PutObjectRequestV2.builder().key(id).bucket(bucket)
      cacheControl.foreach(c => reqBuilder.cacheControl(c))
      mimeType.foreach(m => reqBuilder.contentType(m.name))
      reqBuilder.metadata(meta.asJava)
      val req = reqBuilder.build()

      Stopwatch(s"S3 client.putObject ($req)"){
        clientV2.putObject(req, RequestBody.fromFile(file))
        // once we've completed the PUT read back to ensure that we are returning reality
        val metadata = clientV2.headObject(
          HeadObjectRequest.builder().key(id).bucket(bucket).build()
        )

        S3Object(bucket, id, metadata.contentLength(), S3Metadata(metadata))
      }(markers)
    }

  def storeIfNotPresentV2(bucket: Bucket, id: Key, file: File, mimeType: Option[MimeType], meta: UserMetadata = Map.empty, cacheControl: Option[String] = None)
                       (implicit ex: ExecutionContext, logMarker: LogMarker): Future[S3Object] = {
    Future {
      Some(clientV2.headObject(
        HeadObjectRequest.builder().key(id).bucket(bucket).build()
      ))
    }.recover {
      // translate this exception into the object not existing
      case _: NoSuchKeyException => None
    }.flatMap {
      case Some(metadata) =>
        logger.info(logMarker, s"Skipping storing of S3 file $id as key is already present in bucket $bucket")
        Future.successful(S3Object(bucket, id, metadata.contentLength(), S3Metadata(metadata)))
      case None =>
        storeV2(bucket, id, file, mimeType, meta, cacheControl)
    }
  }

  def listV2(bucket: Bucket, prefixDir: String)
            (implicit ex: ExecutionContext): Future[List[S3Object]] =
    Future {
      val req = ListObjectsV2Request.builder().bucket(bucket).prefix(s"$prefixDir/").build()
      val listing = clientV2.listObjectsV2(req)
      val s3Objects = listing.contents().asScala.toList
      s3Objects.map(s3Object => {
        S3Object(bucket, s3Object.key(), size = s3Object.size(), metadata = getMetadataV2(bucket, s3Object.key()))
      })
    }

  def getMetadataV2(bucket: Bucket, key: Key): S3Metadata = {
    val meta = clientV2.headObject(HeadObjectRequest.builder().key(key).bucket(bucket).build())
    S3Metadata(meta)
  }

  def doesObjectExistV2(bucket: Bucket, key: String) = {
    try {
      clientV2.headObject(
        HeadObjectRequest.builder().key(key).bucket(bucket).build()
      )
      true
    } catch {
      case _: NoSuchKeyException => false
    }
  }

  def deleteObjectV2(bucket: Bucket, key: String): Unit =
    clientV2.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())

  def deleteObjectsV2(bucket: Bucket, keys: List[String]): Map[String, Boolean] = {
    val objects: util.List[ObjectIdentifier] = keys.map { key =>
      ObjectIdentifier.builder()
        .key(key)
        .build()
    }.asJava
    val response = clientV2.deleteObjects(
      DeleteObjectsRequest.builder().bucket(bucket)
        .delete(Delete.builder().objects(objects).build())
        .build()
    )
    val errorKeys = response.errors().asScala.toList.map(_.key())
    keys.map { key =>
      key -> !errorKeys.contains(key)
    }.toMap
  }

  def deleteVersionV2(bucket: Bucket, key: String, objectVersion: String): Unit =
    clientV2.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).versionId(objectVersion).build())

  def copyV2(key: String, sourceBucket: String, destinationBucket: String): CopyObjectResponse = {
    clientV2.copyObject(
      CopyObjectRequest.builder()
        .sourceBucket(sourceBucket)
        .sourceKey(key)
        .destinationBucket(destinationBucket)
        .destinationKey(key)
        .build()
    )
  }
}

object S3Ops {
  // TODO make this localstack friendly
  // TODO: Make this region aware - i.e. RegionUtils.getRegion(region).getServiceEndpoint(AmazonS3.ENDPOINT_PREFIX)
  val s3Endpoint = "s3.amazonaws.com"

  def buildS3ClientV2(config: CommonConfig, localstackAware: Boolean = true, maybeRegionOverride: Option[Region] = None): S3Client = {
    val builder = config.awsLocalEndpoint match {
      case Some(_) if config.isDev =>
        S3Client.builder().forcePathStyle(true)
      case _ => S3Client.builder()
    }

    config.withAWSCredentialsV2(builder, localstackAware, maybeRegionOverride).build()
  }

  def buildS3PresignerV2(config: CommonConfig, localstackAware: Boolean = true, maybeRegionOverride: Option[Region] = None): S3Presigner = {
    val builder = S3Presigner.builder()
      .credentialsProvider(config.awsCredentialsV2)
      .region(maybeRegionOverride.getOrElse(config.awsRegionV2))

    val configuredBuilder = config.awsLocalEndpointUri match {
      case Some(endpoint) if localstackAware => builder.endpointOverride(endpoint)
      case _ => builder
    }

    configuredBuilder.build()
  }
}

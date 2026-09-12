package com.gu.mediaservice.lib

import _root_.play.api.libs.json._
import com.gu.mediaservice.lib.aws.{S3Bucket, S3Object}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.{Embedding, Instance, MimeType}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.model.GetObjectResponse

import java.io.File
import scala.concurrent.Future

object ImageIngestOperations {
  def fileKeyFromId(id: String)(implicit instance: Instance): String = instance.id + "/" + snippetForId(id)

  def optimisedPngKeyFromId(id: String)(implicit instance: Instance): String = instance.id + "/" + "optimised/" + snippetForId(id: String)

  def embeddingKeyFromId(id: String)(implicit instance: Instance): String = instance.id + "/" + snippetForId(id)

  private def snippetForId(id: String) = id.take(6).mkString("/") + "/" + id
}

class ImageIngestOperations(imageBucket: S3Bucket, thumbnailBucket: S3Bucket, embeddingSourceBucket: S3Bucket, embeddingsBucket: S3Bucket, config: CommonConfig, isVersionedS3: Boolean = false)
  extends S3ImageStorage(config) with StrictLogging {

  import ImageIngestOperations.{embeddingKeyFromId, fileKeyFromId, optimisedPngKeyFromId}

  def store(storableImage: StorableImage)
           (implicit logMarker: LogMarker): Future[S3Object] = storableImage match {
    case s:StorableOriginalImage => storeOriginalImage(s)
    case s:StorableThumbImage => storeThumbnailImage(s)
    case s:StorableOptimisedImage => storeOptimisedImage(s)
    case s:StorableEmbeddingSourceImage => storeEmbeddingSourceImage(s)
  }

  private def storeOriginalImage(storableImage: StorableOriginalImage)
                        (implicit logMarker: LogMarker): Future[S3Object] = {
    val instanceSpecificKey = instanceAwareOriginalImageKey(storableImage)
    logger.info(s"Storing original image to instance specific key:${imageBucket.name} / $instanceSpecificKey")
    storeImage(imageBucket, instanceSpecificKey, storableImage.file, Some(storableImage.mimeType),
      storableImage.meta, overwrite = false)
  }

  private def storeThumbnailImage(storableImage: StorableThumbImage)
                                 (implicit logMarker: LogMarker): Future[S3Object] = {
    val instanceSpecificKey = instanceAwareThumbnailImageKey(storableImage)
    logger.info(s"Storing thumbnail to instance specific key: ${thumbnailBucket.name} / $instanceSpecificKey")
    storeImage(thumbnailBucket, instanceSpecificKey, storableImage.file, Some(storableImage.mimeType),
      overwrite = true)
  }

  private def storeOptimisedImage(storableImage: StorableOptimisedImage)
                                 (implicit logMarker: LogMarker): Future[S3Object] = {
    val instanceSpecificKey = optimisedPngKeyFromId(storableImage.id)(storableImage.instance)
    logger.info(s"Storing optimised image to instance specific key: ${thumbnailBucket.name} / $instanceSpecificKey")
    storeImage(imageBucket, instanceSpecificKey, storableImage.file, Some(storableImage.mimeType),
      overwrite = true)
  }

  private def storeEmbeddingSourceImage(storableImage: StorableEmbeddingSourceImage)
                                       (implicit logMarker: LogMarker): Future[S3Object] = {
    val instanceSpecificKey = fileKeyFromId(storableImage.id)(storableImage.instance)
    logger.info(s"Storing embedding source to instance specific key: ${embeddingSourceBucket.name} / $instanceSpecificKey")
    storeImage(embeddingSourceBucket, instanceSpecificKey, storableImage.file, Some(storableImage.mimeType),
      overwrite = true)
  }

  def getEmbeddingStoreImage(key: String): ResponseInputStream[GetObjectResponse] = {
    getObject(embeddingSourceBucket, key)
  }

  def storeEmbedding(key: String, embedding: Embedding): Unit = {
    logger.info(s"Storing embedding source to key: ${embeddingsBucket.name} / $key")
    putString(embeddingsBucket, key, Json.stringify(Json.toJson(embedding)))
  }

  private def bulkDelete(bucket: S3Bucket, keys: List[String]): Future[Map[String, Boolean]] = keys match {
    case Nil => Future.successful(Map.empty)
    case _ => Future {
      deleteObjects(bucket, keys)
    }
  }

  def deleteOriginal(id: String)(implicit logMarker: LogMarker, instance: Instance): Future[Unit] = if(isVersionedS3) deleteVersionedImage(imageBucket, fileKeyFromId(id)) else deleteImage(imageBucket, fileKeyFromId(id))
  def deleteOriginals(ids: Set[String])(implicit instance: Instance) = bulkDelete(imageBucket, ids.map(id => fileKeyFromId(id)).toList)
  def deleteThumbnail(id: String)(implicit logMarker: LogMarker, instance: Instance): Future[Unit] = deleteImage(thumbnailBucket, fileKeyFromId(id))
  def deleteThumbnails(ids: Set[String])(implicit instance: Instance) = bulkDelete(thumbnailBucket, ids.map(id => fileKeyFromId(id)).toList)
  def deletePNG(id: String)(implicit logMarker: LogMarker, instance: Instance): Future[Unit] = deleteImage(imageBucket, optimisedPngKeyFromId(id))
  def deletePNGs(ids: Set[String])(implicit instance: Instance) = bulkDelete(imageBucket, ids.map(id => optimisedPngKeyFromId(id)).toList)
  def deleteEmbeddings(ids: Set[String])(implicit instance: Instance) = bulkDelete(embeddingsBucket, ids.map(id => embeddingKeyFromId(id)).toList)

  def doesOriginalExist(id: String)(implicit instance: Instance): Boolean =
    this.doesObjectExist(imageBucket, fileKeyFromId(id))

  private def instanceAwareOriginalImageKey(storableImage: StorableOriginalImage) = {
    fileKeyFromId(storableImage.id)(storableImage.instance)
  }

  private def instanceAwareThumbnailImageKey(storableImage: StorableThumbImage) = {
    fileKeyFromId(storableImage.id)(storableImage.instance)
  }

}

sealed trait ImageWrapper {
  val id: String
  val file: File
  val mimeType: MimeType
  val meta: Map[String, String]
  val instance: Instance
}
sealed trait StorableImage extends ImageWrapper {
  def toProjectedS3Object(thumbBucket: S3Bucket): S3Object = S3Object(
    thumbBucket,
    ImageIngestOperations.fileKeyFromId(id)(instance),
    file,
    Some(mimeType),
    lastModified = None,
    meta
  )
}

case class StorableThumbImage(id: String, file: File, mimeType: MimeType, meta: Map[String, String] = Map.empty, instance: Instance) extends StorableImage
case class StorableOriginalImage(id: String, file: File, mimeType: MimeType, lastModified: DateTime, meta: Map[String, String] = Map.empty, instance: Instance) extends StorableImage {
  override def toProjectedS3Object(thumbBucket: S3Bucket): S3Object = S3Object(
    thumbBucket,
    ImageIngestOperations.fileKeyFromId(id)(instance),
    file,
    Some(mimeType),
    lastModified = Some(lastModified),
    meta
  )
}
case class StorableOptimisedImage(id: String, file: File, mimeType: MimeType, meta: Map[String, String] = Map.empty, instance: Instance) extends StorableImage {
  override def toProjectedS3Object(thumbBucket: S3Bucket): S3Object = S3Object(
    thumbBucket,
    ImageIngestOperations.optimisedPngKeyFromId(id)(instance),
    file,
    Some(mimeType),
    lastModified = None,
    meta = meta
  )
}
case class StorableEmbeddingSourceImage(id: String, file: File, mimeType: MimeType, meta: Map[String, String] = Map.empty, instance: Instance) extends StorableImage {
  override def toProjectedS3Object(embeddingSourcesBucket: S3Bucket): S3Object = S3Object(
    embeddingSourcesBucket,
    ImageIngestOperations.fileKeyFromId(id)(instance),
    file,
    Some(mimeType),
    lastModified = None,
    meta = meta
  )
}

/**
  * @param id
  * @param file
  * @param mimeType
  * @param meta
  * @param isTransformedFromSource a hint as to whether the Grid has transcoded this image earlier in the pipeline.
  *                                Can be used in order to skip e.g. the stripping of incorrect colour profiles,
  *                                as in this case we have already inferred the profile upstream.
  */
case class BrowserViewableImage(id: String, file: File, mimeType: MimeType, meta: Map[String, String] = Map.empty, isTransformedFromSource: Boolean = false, instance: Instance) extends ImageWrapper {
  def asStorableOptimisedImage = StorableOptimisedImage(id, file, mimeType, meta, instance)
  def asStorableThumbImage = StorableThumbImage(id, file, mimeType, meta, instance)
  def asStorableEmbeddingSourceImage = StorableEmbeddingSourceImage(id, file, mimeType, meta, instance)
}


package com.gu.mediaservice.lib

import com.amazonaws.services.s3.model.{DeleteObjectsRequest, MultiObjectDeleteException}

import java.io.File
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.aws.S3Object
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.{Instance, MimeType, Png}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

object ImageIngestOperations {
  def fileKeyFromId(id: String): String = id.take(6).mkString("/") + "/" + id

  def optimisedPngKeyFromId(id: String, instance: Instance): String = instance.id + "/" + "optimised/" + fileKeyFromId(id: String)
}

class ImageIngestOperations(imageBucket: String, thumbnailBucket: String, config: CommonConfig, isVersionedS3: Boolean = false)
  extends S3ImageStorage(config) with StrictLogging {

  import ImageIngestOperations.{fileKeyFromId, optimisedPngKeyFromId}

  def store(storableImage: StorableImage)
           (implicit logMarker: LogMarker): Future[S3Object] = storableImage match {
    case s:StorableOriginalImage => storeOriginalImage(s)
    case s:StorableThumbImage => storeThumbnailImage(s)
    case s:StorableOptimisedImage => storeOptimisedImage(s)
  }

  private def storeOriginalImage(storableImage: StorableOriginalImage)
                        (implicit logMarker: LogMarker): Future[S3Object] = {
    val instanceSpecificKey = instanceAwareOriginalImageKey(storableImage)
    logger.info(s"Storing original image to instance specific key:$imageBucket / $instanceSpecificKey")
    storeImage(imageBucket, instanceSpecificKey, storableImage.file, Some(storableImage.mimeType),
      storableImage.meta, overwrite = false)
  }

  private def storeThumbnailImage(storableImage: StorableThumbImage)
                                 (implicit logMarker: LogMarker): Future[S3Object] = {
    val instanceSpecificKey = instanceAwareThumbnailImageKey(storableImage)
    logger.info(s"Storing thumbnail to instance specific key: $thumbnailBucket / $instanceSpecificKey")
    storeImage(thumbnailBucket, instanceSpecificKey, storableImage.file, Some(storableImage.mimeType),
      overwrite = true)
  }

  private def storeOptimisedImage(storableImage: StorableOptimisedImage)
                                 (implicit logMarker: LogMarker): Future[S3Object] = {
    val instanceSpecificKey = optimisedPngKeyFromId(storableImage.id, storableImage.instance)
    logger.info(s"Storing optimised image to instance specific key: $thumbnailBucket / $instanceSpecificKey")
    storeImage(imageBucket, instanceSpecificKey, storableImage.file, Some(storableImage.mimeType),
      overwrite = true)
  }

  private def bulkDelete(bucket: String, keys: List[String]): Future[Map[String, Boolean]] = keys match {
    case Nil => Future.successful(Map.empty)
    case _ => Future {
      try {
        client.deleteObjects(
          new DeleteObjectsRequest(bucket).withKeys(keys: _*)
        )
        keys.map { key =>
          key -> true
        }.toMap
      } catch {
        case partialFailure: MultiObjectDeleteException =>
          logger.warn(s"Partial failure when deleting images from $bucket: ${partialFailure.getMessage} ${partialFailure.getErrors}")
          val errorKeys = partialFailure.getErrors.asScala.map(_.getKey).toSet
          keys.map { key =>
            key -> !errorKeys.contains(key)
          }.toMap
      }
    }
  }

  def deleteOriginal(id: String)(implicit logMarker: LogMarker): Future[Unit] = if(isVersionedS3) deleteVersionedImage(imageBucket, fileKeyFromId(id)) else deleteImage(imageBucket, fileKeyFromId(id))
  def deleteOriginals(ids: Set[String]) = bulkDelete(imageBucket, ids.map(fileKeyFromId).toList)
  def deleteThumbnail(id: String)(implicit logMarker: LogMarker): Future[Unit] = deleteImage(thumbnailBucket, fileKeyFromId(id))
  def deleteThumbnails(ids: Set[String]) = bulkDelete(thumbnailBucket, ids.map(fileKeyFromId).toList)
  def deletePNG(id: String)(implicit logMarker: LogMarker): Future[Unit] = deleteImage(imageBucket, optimisedPngKeyFromId(id, ???))
  def deletePNGs(ids: Set[String]) = bulkDelete(imageBucket, ids.map( id => optimisedPngKeyFromId(id, ???)).toList)

  def doesOriginalExist(id: String): Boolean =
    client.doesObjectExist(imageBucket, fileKeyFromId(id))

  private def instanceAwareOriginalImageKey(storableImage: StorableOriginalImage) = {
    storableImage.instance + "/" + fileKeyFromId(storableImage.id)
  }

  private def instanceAwareThumbnailImageKey(storableImage: StorableThumbImage) = {
    storableImage.instance + "/" + fileKeyFromId(storableImage.id)
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
  def toProjectedS3Object(thumbBucket: String): S3Object = S3Object(
    thumbBucket,
    ImageIngestOperations.fileKeyFromId(id),
    file,
    Some(mimeType),
    lastModified = None,
    meta
  )
}

case class StorableThumbImage(id: String, file: File, mimeType: MimeType, meta: Map[String, String] = Map.empty, instance: Instance) extends StorableImage
case class StorableOriginalImage(id: String, file: File, mimeType: MimeType, lastModified: DateTime, meta: Map[String, String] = Map.empty, instance: Instance) extends StorableImage {
  override def toProjectedS3Object(thumbBucket: String): S3Object = S3Object(
    thumbBucket,
    ImageIngestOperations.fileKeyFromId(id),
    file,
    Some(mimeType),
    lastModified = Some(lastModified),
    meta
  )
}
case class StorableOptimisedImage(id: String, file: File, mimeType: MimeType, meta: Map[String, String] = Map.empty, instance: Instance) extends StorableImage {
  override def toProjectedS3Object(thumbBucket: String): S3Object = S3Object(
    thumbBucket,
    ImageIngestOperations.optimisedPngKeyFromId(id, instance),
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
}


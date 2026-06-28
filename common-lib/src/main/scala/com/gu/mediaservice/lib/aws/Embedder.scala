package com.gu.mediaservice.lib.aws
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.mediaservice.lib.embeddings.EmbeddingImplementation
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model.ImageMetadata
import play.api.libs.json.{Json, OFormat}

import scala.concurrent.{ExecutionContext, Future}

case class EmbedderMessage(imageId: String, fileType: String, s3Bucket: String, s3Key: String, instance: String)

object EmbedderMessage {
  implicit val format: OFormat[EmbedderMessage] = Json.format[EmbedderMessage]
}

class Embedder(embedding: EmbeddingImplementation, sqs: SimpleSqsMessageConsumer)(implicit ec: ExecutionContext) extends GridLogging {

  def createQueryEmbedding(query: String)(implicit logMarker: LogMarker): Future[List[Float]] = {
    logger.info(logMarker, s"Creating text embedding for query: $query")
    for {
      embedding <- embedding.createTextEmbedding(query)
    } yield embedding
  }

  def createImageEmbedding(source: Array[Byte], maybeMetadata: Option[ImageMetadata])(implicit logMarker: LogMarker): Future[List[Float]] = {
    logger.info(logMarker, s"Creating image embedding")
    embedding.createImageEmbeddings(source, maybeMetadata)
  }

  def queueImageToEmbed(message: EmbedderMessage)(implicit logMarker: LogMarker) = {
    val messageBody = Json.stringify(Json.toJson(message))
    val result: SendMessageResult = sqs.sendMessage(messageBody)
    logger.info(logMarker, s"Queued image for embedding with message ID: ${result.getMessageId}")
  }
}

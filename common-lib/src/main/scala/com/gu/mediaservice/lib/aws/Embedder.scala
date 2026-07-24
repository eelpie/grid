package com.gu.mediaservice.lib.aws
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model.{Embedding, ImageMetadata, MimeType}
import play.api.libs.json.{Json, OFormat}
import software.amazon.awssdk.services.sqs.model.SendMessageResponse

import scala.concurrent.{ExecutionContext, Future}

case class EmbedderMessage(imageId: String, fileType: String, s3Bucket: String, s3Key: String, instance: String)

object EmbedderMessage {
  implicit val format: OFormat[EmbedderMessage] = Json.format[EmbedderMessage]
}

class Embedder(bedrock: Bedrock, sqs: SimpleSqsMessageConsumer)(implicit ec: ExecutionContext) extends GridLogging {

  def createQueryEmbedding(query: String)(implicit logMarker: LogMarker): Future[List[Double]] = {
    logger.info(logMarker, s"Creating text embedding for query: $query")
    for {
      embedding <- bedrock.createTextEmbedding(query)
    } yield embedding
  }

  def createImageEmbedding(source: Array[Byte], mimeType: MimeType, maybeMetadata: Option[ImageMetadata])(implicit logMarker: LogMarker): Future[Embedding] = {
    logger.info(logMarker, s"Creating image embedding")
    bedrock.createImageEmbeddings(source, mimeType, maybeMetadata)
  }

  def queueImageToEmbed(message: EmbedderMessage)(implicit logMarker: LogMarker) = {
    val messageBody = Json.stringify(Json.toJson(message))
    val result: SendMessageResponse = sqs.sendMessage(messageBody)
    logger.info(logMarker, s"Queued image for embedding with message ID: ${result.messageId()}")
  }
}

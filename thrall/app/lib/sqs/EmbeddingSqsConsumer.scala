package lib.sqs

import com.gu.mediaservice.lib.ImageIngestOperations
import com.gu.mediaservice.lib.aws.{Embedder, EmbedderMessage, ThrallMessageSender}
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap}
import com.gu.mediaservice.model.{Instance, MimeType, UpdateEmbeddingMessage}
import com.typesafe.scalalogging.StrictLogging
import lib.ThrallStore
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.connectors.sqs.scaladsl.{SqsAckFlow, SqsSource}
import org.apache.pekko.stream.connectors.sqs.{MessageAction, SqsSourceSettings}
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import org.joda.time.DateTime
import play.api.libs.json.Json
import software.amazon.awssdk.services.sqs.SqsAsyncClient

import scala.concurrent.{ExecutionContext, Future}

class EmbeddingSqsConsumer(queueUrl: String, sqsClient: SqsAsyncClient, embedder: Embedder, thrallStore: ThrallStore, messageSender: ThrallMessageSender)
                          (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  extends StrictLogging {

  private val sourceSettings = SqsSourceSettings.Defaults
  private implicit val logMarker: LogMarker = MarkerMap()

  def start(): Future[_] = {
    logger.info(s"Starting Pekko Connectors SQS consumer on $queueUrl")
    SqsSource(queueUrl, sourceSettings)(sqsClient)
      .map { message =>
        logger.info(s"Received SQS message id=${message.messageId()} body=${message.body()}")

        val maybeParsed = Json.parse(message.body()).validate[EmbedderMessage].asOpt
        logger.info("Parsed: " + maybeParsed)

        maybeParsed.map { parsed =>
          // TODO check file exists
          val s3Object = thrallStore.getEmbeddingStoreImage(parsed.s3Key) // TODO imageid to keep knowledge of path in the store
          val response = s3Object.response()
          val bytes =
            try s3Object.readAllBytes()
            finally s3Object.close()

          // Take the source image mimeType from S3 metadata for embedders who want it
          val maybeMimeTypeHeader = Option(response.contentType())
            .filterNot(_.equalsIgnoreCase("application/octet-stream"))
          val maybeMimeType = maybeMimeTypeHeader.map(MimeType(_)) // TODO recover to None
          logger.info(s"Got embedding source with mineType $maybeMimeTypeHeader / $maybeMimeType and image metadata title: ${parsed.imageMetadata.flatMap(_.title)}")

          maybeMimeType.map { mimeType =>
            val eventualEmbedding = embedder.createImageEmbedding(bytes, mimeType, parsed.imageMetadata)
            eventualEmbedding.map { embedding =>
              logger.info("Got embedding: " + embedding)

              // Store the embedding for reindexing
              thrallStore.storeEmbedding(ImageIngestOperations.embeddingKeyFromId(parsed.imageId)(Instance(parsed.instance)), embedding)

              // Issue an UpdateEmbedding message
              val updateEmbeddingMessage = UpdateEmbeddingMessage(
                id = parsed.imageId,
                lastModified = DateTime.now, // TODO check this against the lambda
                embedding = embedding,
                instance = Instance(id = parsed.instance)
              )
              messageSender.publish(updateEmbeddingMessage)
            }

          }.getOrElse {
            logger.warn("Skipping embedding source with missing mimeType: " + parsed.s3Key)
            Future.successful(())
          }
        }

        MessageAction.delete(message)
      }
      .via(SqsAckFlow(queueUrl)(sqsClient))
      .toMat(Sink.foreach { result =>
        logger.debug(s"Acked SQS message: $result")
      })(Keep.right)
      .run()
  }
}

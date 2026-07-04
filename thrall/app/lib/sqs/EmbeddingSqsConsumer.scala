package lib.sqs

import com.amazonaws.util.IOUtils
import com.gu.mediaservice.lib.aws.{Embedder, EmbedderMessage, ThrallMessageSender}
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap}
import com.gu.mediaservice.model.{CohereV4Embedding, Embedding, Instance, UpdateEmbeddingMessage}
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

import java.io.ByteArrayOutputStream
import scala.concurrent.{ExecutionContext, Future}

class EmbeddingSqsConsumer(queueUrl: String, sqsClient: SqsAsyncClient, embedder: Embedder, thrallStore: ThrallStore, lowPriorityMessageSender: ThrallMessageSender)
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
          val bos = new ByteArrayOutputStream()
          try {
            IOUtils.copy(s3Object.getObjectContent, bos)
          } finally {
            s3Object.close()
          }
          val eventualEmbedding = embedder.createImageEmbedding(bos.toByteArray, None)
          eventualEmbedding.map { embedding =>
            logger.info("Got embedding: " + embedding)
            // Issue an UpdateEmbedding message
            val updateEmbeddingMessage = UpdateEmbeddingMessage(
              id = parsed.imageId,
              lastModified = DateTime.now, // TODO check this against the lambda
              embedding = embedding,
              instance = Instance(id = parsed.instance)
            )
            lowPriorityMessageSender.publish(updateEmbeddingMessage)
            ()
          }

        }.getOrElse {
          Future.successful(())
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

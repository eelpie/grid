package lib.sqs

import com.gu.mediaservice.lib.aws.{Embedder, EmbedderMessage}
import com.typesafe.scalalogging.StrictLogging
import lib.ThrallStore
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.connectors.sqs.scaladsl.{SqsAckFlow, SqsSource}
import org.apache.pekko.stream.connectors.sqs.{MessageAction, SqsSourceSettings}
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import play.api.libs.json.Json
import software.amazon.awssdk.services.sqs.SqsAsyncClient

import scala.concurrent.{ExecutionContext, Future}

class EmbeddingSqsConsumer(queueUrl: String, sqsClient: SqsAsyncClient, embedder: Embedder, thrallStore: ThrallStore)
                          (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  extends StrictLogging {

  private val sourceSettings = SqsSourceSettings.Defaults

  def start(): Future[_] = {
    logger.info(s"Starting Pekko Connectors SQS consumer on $queueUrl")
    SqsSource(queueUrl, sourceSettings)(sqsClient)
      .map { message =>
        logger.info(s"Received SQS message id=${message.messageId()} body=${message.body()}")

        val maybeParsed = Json.parse(message.body()).validate[EmbedderMessage].asOpt
        logger.info("Parsed: " + maybeParsed)

        MessageAction.delete(message)
      }
      .via(SqsAckFlow(queueUrl)(sqsClient))
      .toMat(Sink.foreach { result =>
        logger.debug(s"Acked SQS message: $result")
      })(Keep.right)
      .run()
  }
}

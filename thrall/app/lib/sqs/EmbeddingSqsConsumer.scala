package lib.sqs

import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.connectors.sqs.scaladsl.{SqsAckFlow, SqsSource}
import org.apache.pekko.stream.connectors.sqs.{MessageAction, SqsSourceSettings}
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import software.amazon.awssdk.services.sqs.SqsAsyncClient

import scala.concurrent.{ExecutionContext, Future}

class EmbeddingSqsConsumer(queueUrl: String, sqsClient: SqsAsyncClient)
                          (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  extends StrictLogging {

  private val sourceSettings = SqsSourceSettings.Defaults

  def start(): Future[_] = {
    logger.info(s"Starting Pekko Connectors SQS consumer on $queueUrl")
    SqsSource(queueUrl, sourceSettings)(sqsClient)
      .map { message =>
        logger.info(s"Received SQS message id=${message.messageId()} body=${message.body()}")
        MessageAction.delete(message)
      }
      .via(SqsAckFlow(queueUrl)(sqsClient))
      .toMat(Sink.foreach { result =>
        logger.debug(s"Acked SQS message: $result")
      })(Keep.right)
      .run()
  }
}

package lib

import com.gu.kinesis.KinesisRecord
import com.gu.mediaservice.lib.DateTimeUtils
import com.gu.mediaservice.lib.logging._
import com.gu.mediaservice.model.{ExternalThrallMessage, MigrationMessage, ThrallMessage}
import lib.kinesis.ThrallEventConsumer
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{GraphDSL, MergePreferred, Source}
import org.apache.pekko.stream.{Materializer, SourceShape}
import org.apache.pekko.{Done, NotUsed}

import java.time.Instant
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

sealed trait Priority
case object UiPriority extends Priority {
  override def toString = "high"
}
case object AutomationPriority extends Priority {
  override def toString = "low"
}
case object MigrationPriority extends Priority {
  override def toString = "lowest"
}

/** TaggedRecord represents a message and its associated priority
  *
  * Type parameter P represents the type of the payload, so TaggedRecord
  * can be used to represent both messages from Kinesis and messages
  * originating within thrall itself
*/
case class TaggedRecord[+P](payload: P,
                           arrivalTimestamp: Instant,
                           priority: Priority,
                           markProcessed: () => Unit) extends LogMarker {
  override def markerContents: Map[String, Any] = (payload match {
    case withMarker:LogMarker => withMarker.markerContents
    case _ => Map.empty[String, Any]
  }) ++ Map(
    "recordPriority" -> priority.toString,
    "recordArrivalTime" -> DateTimeUtils.toString (arrivalTimestamp)
  )

  def map[V](f: P => V): TaggedRecord[V] = this.copy(payload = f(payload))
}

class ThrallStreamProcessor(
  uiSource: Source[KinesisRecord, Future[Done]],
  automationSource: Source[KinesisRecord, Future[Done]],
  migrationSource: Source[MigrationRecord, Future[Done]],
  consumer: ThrallEventConsumer,
  actorSystem: ActorSystem
 ) extends GridLogging {

  implicit val mat: Materializer = Materializer.matFromSystem(actorSystem)
  implicit val dispatcher: ExecutionContextExecutor = actorSystem.getDispatcher

  val mergedKinesisSource: Source[TaggedRecord[ThrallMessage], NotUsed] = Source.fromGraph(GraphDSL.create() { implicit graphBuilder =>
    import GraphDSL.Implicits._

    val uiRecordSource: Source[TaggedRecord[Array[Byte]], Future[Done]] = uiSource.map(kinesisRecord =>
      TaggedRecord(kinesisRecord.data.toArray, kinesisRecord.approximateArrivalTimestamp, UiPriority, kinesisRecord.markProcessed))

    val migrationMessagesSource: Source[TaggedRecord[MigrationMessage], Future[Done]] = migrationSource.map { case MigrationRecord(internalThrallMessage, time) =>
      TaggedRecord(internalThrallMessage, time, MigrationPriority, () => {})
    }

    // parse the kinesis records into thrall update messages (dropping those that fail)
    val uiMessagesSource: Source[TaggedRecord[ExternalThrallMessage], Future[Done]] = uiRecordSource.map { taggedRecord =>
          val parsedRecord = ThrallEventConsumer
            .parseRecord(taggedRecord.payload, taggedRecord.arrivalTimestamp)
            .map(
              message => taggedRecord.copy(payload = message)
            )
          // If we failed to parse the record (Left), we'll drop it below because we can't process it.
          // However we still need to mark the record as processed, otherwise the kinesis stream can't progress
          // and checkpoint will be stuck at this message forevermore.
          parsedRecord.left.foreach(_ => taggedRecord.markProcessed())
          parsedRecord
        }
        // drop unparseable records
        .collect {
          case Right(taggedRecord) => taggedRecord
        }

    // merge in the re-ingestion source (preferring ui/automation)
    val mergePreferred = graphBuilder.add(MergePreferred[TaggedRecord[ThrallMessage]](1))
    uiMessagesSource ~> mergePreferred.preferred
    migrationMessagesSource ~> mergePreferred.in(0)

    val out: SourceShape[TaggedRecord[ThrallMessage]] = SourceShape(mergePreferred.out)
    out
  })

  val automationKinesisSource: Source[TaggedRecord[ThrallMessage], NotUsed] = {
    val z: Source[TaggedRecord[ExternalThrallMessage], NotUsed] = Source.fromGraph(GraphDSL.create() { implicit graphBuilder =>
      val automationRecordSource: Source[TaggedRecord[Array[Byte]], Future[Done]] = automationSource.map(kinesisRecord =>
        TaggedRecord(kinesisRecord.data.toArray, kinesisRecord.approximateArrivalTimestamp, AutomationPriority, kinesisRecord.markProcessed))

      // parse the kinesis records into thrall update messages (dropping those that fail)
      val automationMessagesSource: Source[TaggedRecord[ExternalThrallMessage], Future[Done]] = automationRecordSource.map { taggedRecord =>
          val parsedRecord = ThrallEventConsumer
            .parseRecord(taggedRecord.payload, taggedRecord.arrivalTimestamp)
            .map(
              message => taggedRecord.copy(payload = message)
            )
          // If we failed to parse the record (Left), we'll drop it below because we can't process it.
          // However we still need to mark the record as processed, otherwise the kinesis stream can't progress
          // and checkpoint will be stuck at this message forevermore.
          parsedRecord.left.foreach(_ => taggedRecord.markProcessed())
          parsedRecord
        }
        // drop unparseable records
        .collect {
          case Right(taggedRecord) => taggedRecord
        }

      val out: SourceShape[TaggedRecord[ExternalThrallMessage]] = automationMessagesSource.shape
      out
    })
    z
  }

  def createUIStream(): Source[(TaggedRecord[ThrallMessage], Stopwatch, ThrallMessage), NotUsed] = {
    mergedKinesisSource.mapAsync(1) { result =>
      val stopwatch = Stopwatch.start
      consumer.processMessage(result.payload)
        .recover { case _ => () }
        .map(_ => (result, stopwatch, result.payload))
    }
  }

  def createAutomationStream(): Source[(TaggedRecord[ThrallMessage], Stopwatch, ThrallMessage), NotUsed] = {
    automationKinesisSource.mapAsync(20) { result =>
      val stopwatch = Stopwatch.start
      consumer.processMessage(result.payload)
        .recover { case _ => () }
        .map(_ => (result, stopwatch, result.payload))
      }
  }

  def run(): Future[Done] = {
    val stream = this.createUIStream().runForeach {
      case (taggedRecord, stopwatch, _) =>
        val markers = combineMarkers(taggedRecord, stopwatch.elapsed)
        logger.info(markers, "Record processed")
        taggedRecord.markProcessed()
    }
    stream.onComplete {
      case Failure(exception) => logger.error("Thrall stream completed with failure", exception)
      case Success(_) => logger.info("Thrall stream completed with done, probably shutting down")
    }

    val automationStream = this.createAutomationStream().runForeach {
      case (taggedRecord, stopwatch, _) =>
        val markers = combineMarkers(taggedRecord, stopwatch.elapsed)
        logger.info(markers, "Record processed")
        taggedRecord.markProcessed()
    }
    automationStream.onComplete {
      case Failure(exception) => logger.error("Thrall stream completed with failure", exception)
      case Success(_) => logger.info("Thrall stream completed with done, probably shutting down")
    }

    stream
  }
}


package com.gu.mediaservice.lib.events

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.gu.mediaservice.lib.logging.GridLogging
import com.gu.mediaservice.model.Instance
import org.joda.time.DateTime
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JodaWrites, Json, OWrites}
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

import scala.concurrent.duration.DurationInt
import scala.util.Random

class UsageEvents(actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle, sqsClient: SqsClient, queueUrl: String) {

  private val random = new Random()
  private val usageEventsActor = actorSystem.actorOf(UsageEventsActor.props(sqsClient, queueUrl), s"usageeventsactor-${random.alphanumeric.take(8).mkString}")

  applicationLifecycle.addStopHook(() => (usageEventsActor ? UsageEventsActor.Shutdown)(Timeout(5.seconds)))

  def successfulIngestFromQueue(instance: Instance, image: String, filesize: Long): Unit = {
    usageEventsActor ! UsageEvent(`type` = "imageIngest", instance = instance.id, image = Some(image), filesize = Some(filesize))
  }

  def successfulUpload(instance: Instance, image: String, filesize: Long): Unit = {
    usageEventsActor ! UsageEvent(`type` = "imageUpload", instance = instance.id, image = Some(image), filesize = Some(filesize))
  }

  def downloadOriginal(instance: Instance, image: String, filesize: Option[Long]): Unit = {
    usageEventsActor ! UsageEvent(`type` = "downloadOriginal", instance = instance.id, image = Some(image), filesize = filesize)
  }

}

case class UsageEvent(`type`: String, instance: String, image: Option[String], filesize: Option[Long], date: DateTime = DateTime.now)

object UsageEvent extends JodaWrites {
  implicit val uew: OWrites[UsageEvent] = Json.writes[UsageEvent]
}


object UsageEventsActor {
  def props(sqsClient: SqsClient, queueUrl: String): Props =
    Props(new UsageEventsActor(sqsClient, queueUrl))

  final case object Shutdown
}


private class UsageEventsActor(sqsClient: SqsClient, queueUrl: String) extends Actor with GridLogging {
  override def receive: Receive = {
    case usageEvent: UsageEvent =>
      logger.info("Got usageEvent: " + usageEvent)
      send(usageEvent)
  }

  def send(usageEvent: UsageEvent): Unit = {
    import play.api.libs.json._
    val message = Json.toJson(usageEvent).toString()
    sqsClient.sendMessage(SendMessageRequest.builder.queueUrl(queueUrl).messageBody(Json.toJson(message).toString()).build)
  }

}

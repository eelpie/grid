package com.gu.mediaservice.model.usage

import com.gu.mediaservice.lib.dynamo.{DbString, DynamoElement}
import play.api.libs.json._

import java.net.URI

case class DigitalUsageMetadata (
  webUrl: URI,
  webTitle: Option[String],
  sectionId: Option[String],
  composerUrl: Option[URI] = None
) extends UsageMetadata {
  private val placeholderWebTitle = "No title given"
  private val dynamoSafeWebTitle = webTitle.find(_.nonEmpty).getOrElse(placeholderWebTitle)

  override def toMap: Map[String, String] = Map(
    "webUrl" -> webUrl.toString,
    "webTitle" -> dynamoSafeWebTitle
  ) ++ sectionId.filter(_.nonEmpty).map("sectionId" -> _) ++ composerUrl.map("composerUrl" -> _.toString)

  override def toDynamoMap: Map[String, DynamoElement] = Map(
    "webUrl" -> DbString(webUrl.toString),
    "webTitle" -> DbString(dynamoSafeWebTitle),
  ) ++ sectionId.filter(_.nonEmpty).map("sectionId" -> DbString(_)) ++ composerUrl.map(c => "composerUrl" -> DbString(c.toString))
}

object DigitalUsageMetadata {
  implicit val reader: Reads[DigitalUsageMetadata] = Json.reads[DigitalUsageMetadata]
  implicit val writer: Writes[DigitalUsageMetadata] = Json.writes[DigitalUsageMetadata]
}

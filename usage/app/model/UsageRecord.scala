package model

import com.gu.mediaservice.model.usage._
import org.joda.time.DateTime
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue => AttributeValueV2}

import scala.jdk.CollectionConverters._

sealed trait DateRemovedOperation
case object ClearDateRemoved extends DateRemovedOperation
case object LeaveDateRemovedUntouched extends DateRemovedOperation
case class SetDateRemoved(dateRemoved: DateTime) extends DateRemovedOperation

case class UsageRecord(
  hashKey: String,
  rangeKey: String,
  dateRemovedOperation: DateRemovedOperation,
  mediaId: Option[String] = None,
  usageType: Option[UsageType] = None,
  mediaType: Option[String] = None,
  lastModified: Option[DateTime] = None,
  usageStatus: Option[String] = None,
  printUsageMetadata: Option[PrintUsageMetadata] = None,
  digitalUsageMetadata: Option[DigitalUsageMetadata] = None,
  syndicationUsageMetadata: Option[SyndicationUsageMetadata] = None,
  frontUsageMetadata: Option[FrontUsageMetadata] = None,
  downloadUsageMetadata: Option[DownloadUsageMetadata] = None,
  childUsageMetadata: Option[ChildUsageMetadata] = None,
  dateAdded: Option[DateTime] = None
) {
  def toUpdateExpressionV2: (String, Map[String, AttributeValueV2]) = {
    val setFields: List[(String, AttributeValueV2)] = List(
      mediaId.filter(_.nonEmpty).map(mediaId => "media_id" -> AttributeValueV2.fromS(mediaId)),
      usageType.map(usageType => "usage_type" -> AttributeValueV2.fromS(usageType.toString)),
      mediaType.filter(_.nonEmpty).map(mediaType => "media_type" -> AttributeValueV2.fromS(mediaType)),
      lastModified.map(lastModified => "last_modified" -> AttributeValueV2.fromN(lastModified.getMillis.toString)),
      usageStatus.filter(_.nonEmpty).map(usageStatus => "usage_status" -> AttributeValueV2.fromS(usageStatus)),
      printUsageMetadata.map(m => "print_metadata" -> metadataToAttr(m.toMap)),
      digitalUsageMetadata.map(m => "digital_metadata" -> metadataToAttr(m.toMap)),
      syndicationUsageMetadata.map(m => "syndication_metadata" -> metadataToAttr(m.toMap)),
      frontUsageMetadata.map(m => "front_metadata" -> metadataToAttr(m.toMap)),
      downloadUsageMetadata.map(m => "download_metadata" -> metadataToAttr(m.toMap)),
      childUsageMetadata.map(m => "child_metadata" -> metadataToAttr(m.toMap)),
      dateAdded.map(dateAdded => "date_added" -> AttributeValueV2.fromN(dateAdded.getMillis.toString)),
      dateRemovedOperation match {
        case SetDateRemoved(dr) => Some("date_removed" -> AttributeValueV2.fromN(dr.getMillis.toString))
        case _ => None
      }
    ).flatten

    val setExpression = if (setFields.nonEmpty)
      "SET " + setFields.map { case (name, _) => s"$name = :$name" }.mkString(", ")
    else ""

    val removeExpression = dateRemovedOperation match {
      case ClearDateRemoved => "REMOVE date_removed"
      case _ => ""
    }

    val expression = List(setExpression, removeExpression).filter(_.nonEmpty).mkString(" ")
    val values = setFields.map { case (name, value) => s":$name" -> value }.toMap

    (expression, values)
  }

  private def metadataToAttr(m: Map[String, Any]): AttributeValueV2 =
    AttributeValueV2.fromM(m.view.mapValues(anyToAttr).toMap.asJava)

  private def anyToAttr(v: Any): AttributeValueV2 = v match {
    case s: String               => AttributeValueV2.fromS(s)
    case n: Int                  => AttributeValueV2.fromN(n.toString)
    case n: Long                 => AttributeValueV2.fromN(n.toString)
    case n: java.math.BigDecimal => AttributeValueV2.fromN(n.toString)
    case m: java.util.Map[_, _]  =>
      AttributeValueV2.fromM(m.asScala.map { case (k, v) => k.toString -> anyToAttr(v) }.toMap.asJava)
    case _                       => AttributeValueV2.fromNul(true)
  }
}

object UsageRecord {
  def buildMarkAsRemovedRecord(mediaUsage: MediaUsage) = UsageRecord(
    hashKey = mediaUsage.grouping,
    rangeKey = mediaUsage.usageId.toString,
    dateRemovedOperation = SetDateRemoved(mediaUsage.lastModified)
  )

  def buildUpdateRecord(mediaUsage: MediaUsage) = UsageRecord(
    hashKey = mediaUsage.grouping,
    rangeKey = mediaUsage.usageId.toString,
    dateRemovedOperation = LeaveDateRemovedUntouched,
    mediaId = Some(mediaUsage.mediaId),
    usageType = Some(mediaUsage.usageType),
    mediaType = Some(mediaUsage.mediaType),
    lastModified = Some(mediaUsage.lastModified),
    usageStatus = Some(mediaUsage.status.toString),
    printUsageMetadata = mediaUsage.printUsageMetadata,
    digitalUsageMetadata = mediaUsage.digitalUsageMetadata,
    syndicationUsageMetadata = mediaUsage.syndicationUsageMetadata,
    frontUsageMetadata = mediaUsage.frontUsageMetadata,
    downloadUsageMetadata = mediaUsage.downloadUsageMetadata,
    childUsageMetadata = mediaUsage.childUsageMetadata,
  )

  def buildCreateRecord(mediaUsage: MediaUsage) = UsageRecord(
    hashKey = mediaUsage.grouping,
    rangeKey = mediaUsage.usageId.toString,
    dateRemovedOperation = ClearDateRemoved,
    mediaId = Some(mediaUsage.mediaId),
    usageType = Some(mediaUsage.usageType),
    mediaType = Some(mediaUsage.mediaType),
    lastModified = Some(mediaUsage.lastModified),
    usageStatus = Some(mediaUsage.status.toString),
    printUsageMetadata = mediaUsage.printUsageMetadata,
    digitalUsageMetadata = mediaUsage.digitalUsageMetadata,
    syndicationUsageMetadata = mediaUsage.syndicationUsageMetadata,
    frontUsageMetadata = mediaUsage.frontUsageMetadata,
    downloadUsageMetadata = mediaUsage.downloadUsageMetadata,
    childUsageMetadata = mediaUsage.childUsageMetadata,
    dateAdded = Some(mediaUsage.lastModified),
  )
}

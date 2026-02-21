package com.gu.mediaservice.lib.usage

import java.net.URI
import com.gu.mediaservice.model.usage._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue => AttributeValueV2}

import scala.jdk.CollectionConverters._
import scala.util.Try

object ItemToMediaUsage {

  def transformV2(attrs: java.util.Map[String, AttributeValueV2]): MediaUsage = {
    val m = attrs.asScala
    MediaUsage(
      UsageId(m("usage_id").s()),
      m("grouping").s(),
      m("media_id").s(),
      UsageType(m("usage_type").s()),
      m("media_type").s(),
      UsageStatus(m("usage_status").s()),
      m.get("print_metadata").map(_.m().asScala.view.mapValues(attrToAny).toMap).flatMap(buildPrint),
      m.get("digital_metadata").map(_.m().asScala.view.mapValues(attrToAny).toMap).flatMap(buildDigital),
      m.get("syndication_metadata").map(_.m().asScala.view.mapValues(attrToAny).toMap).flatMap(buildSyndication),
      m.get("front_metadata").map(_.m().asScala.view.mapValues(attrToAny).toMap).flatMap(buildFront),
      m.get("download_metadata").map(_.m().asScala.view.mapValues(attrToAny).toMap).flatMap(buildDownload),
      m.get("child_metadata").map(_.m().asScala.view.mapValues(attrToAny).toMap).flatMap(buildChild),
      new DateTime(m("last_modified").n().toLong),
      Try(m("date_added").n().toLong).toOption.map(new DateTime(_)),
      Try(m("date_removed").n().toLong).toOption.map(new DateTime(_))
    )
  }

  private def attrToAny(av: AttributeValueV2): Any = {
    if (av.s() != null) av.s()
    else if (av.n() != null) new java.math.BigDecimal(av.n())
    else if (av.hasM) {
      val linkedMap = new java.util.LinkedHashMap[String, Any]()
      av.m().forEach((k, v) => linkedMap.put(k, attrToAny(v)))
      linkedMap
    }
    else null
  }

  private def buildFront(metadataMap: Map[String, Any]): Option[FrontUsageMetadata] = {
    Try {
      FrontUsageMetadata(
        metadataMap("addedBy").asInstanceOf[String],
        metadataMap("front").asInstanceOf[String]
      )
    }.toOption
  }

  private def buildSyndication(metadataMap: Map[String, Any]): Option[SyndicationUsageMetadata] = {
    Try {
      SyndicationUsageMetadata(
        metadataMap("partnerName").asInstanceOf[String],
        metadataMap.get("syndicatedBy").map(x => x.asInstanceOf[String])
      )
    }.toOption
  }

  private def buildDigital(metadataMap: Map[String, Any]): Option[DigitalUsageMetadata] = {
    Try {
      DigitalUsageMetadata(
        URI.create(metadataMap("webUrl").asInstanceOf[String]),
        metadataMap.get("webTitle").map(x => x.asInstanceOf[String]),
        metadataMap.get("sectionId").map(x => x.asInstanceOf[String]),
        metadataMap.get("composerUrl").map(x => URI.create(x.asInstanceOf[String]))
      )
    }.toOption
  }

  private def buildPrint(metadataMap: Map[String, Any]): Option[PrintUsageMetadata] = {
    type JStringNumMap = java.util.LinkedHashMap[String, java.math.BigDecimal]
    Try {
      PrintUsageMetadata(
        sectionName = metadataMap.apply("sectionName").asInstanceOf[String],
        issueDate = metadataMap.get("issueDate").map(_.asInstanceOf[String])
          .map(ISODateTimeFormat.dateTimeParser().parseDateTime).get,
        pageNumber = metadataMap.apply("pageNumber").asInstanceOf[java.math.BigDecimal].intValue,
        storyName = metadataMap.apply("storyName").asInstanceOf[String],
        publicationCode = metadataMap.apply("publicationCode").asInstanceOf[String],
        publicationName = metadataMap.apply("publicationName").asInstanceOf[String],
        layoutId = metadataMap.get("layoutId").map(_.asInstanceOf[java.math.BigDecimal].intValue),
        edition = metadataMap.get("edition").map(_.asInstanceOf[java.math.BigDecimal].intValue),
        size = metadataMap.get("size")
          .map(_.asInstanceOf[JStringNumMap])
          .map(m => PrintImageSize(m.get("x").intValue, m.get("y").intValue)),
        orderedBy = metadataMap.get("orderedBy").map(_.asInstanceOf[String]),
        sectionCode = metadataMap.apply("sectionCode").asInstanceOf[String],
        notes = metadataMap.get("notes").map(_.asInstanceOf[String]),
        source = metadataMap.get("source").map(_.asInstanceOf[String])
      )
    }.toOption
  }

  private def buildDownload(metadataMap: Map[String, Any]): Option[DownloadUsageMetadata] = {
    Try {
      DownloadUsageMetadata(
        metadataMap("downloadedBy").asInstanceOf[String]
      )
    }.toOption
  }

  private def buildChild(metadataMap: Map[String, Any]): Option[ChildUsageMetadata] = {
    Try {
      ChildUsageMetadata(
        metadataMap("addedBy").asInstanceOf[String],
        metadataMap("childMediaId").asInstanceOf[String],
      )
    }.toOption
  }
}

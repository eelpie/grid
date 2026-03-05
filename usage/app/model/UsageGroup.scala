package model

import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model.usage.{MediaUsage, UsageStatus}
import lib.{MD5, MediaUsageBuilder, UsageConfig}
import org.joda.time.DateTime
import play.api.libs.json._

case class UsageGroup(
  usages: Set[MediaUsage],
  grouping: String,
  lastModified: DateTime,
  isReindex: Boolean = false,
  maybeStatus: Option[UsageStatus] = None
)
class UsageGroupOps(config: UsageConfig)
  extends GridLogging {

  def buildId(digitalMediaUsageRecord: DigitalMediaUsageRecord): String = s"digital/${
    MD5.hash(List(
      digitalMediaUsageRecord.mediaId,
      digitalMediaUsageRecord.metadata.webUrl,
      digitalMediaUsageRecord.dateAdded.getMillis.toString
    ).mkString("_"))
  }"

  def buildId(printUsage: PrintUsageRecord) = s"print/${MD5.hash(List(
    Some(printUsage.mediaId),
    Some(printUsage.printUsageMetadata.pageNumber),
    Some(printUsage.printUsageMetadata.sectionCode),
    Some(printUsage.printUsageMetadata.issueDate)
  ).flatten.map(_.toString).mkString("_"))}"

  def buildId(syndicationUsageRequest: SyndicationUsageRequest): String = s"syndication/${
    MD5.hash(List(
      syndicationUsageRequest.metadata.partnerName,
      syndicationUsageRequest.metadata.syndicatedBy,
      syndicationUsageRequest.mediaId
    ).mkString("_"))
  }"

  def buildId(frontUsageRequest: FrontUsageRequest): String = s"front/${
    MD5.hash(List(
      frontUsageRequest.mediaId,
      frontUsageRequest.metadata.front
    ).mkString("_"))
  }"

  def buildId(downloadUsageRequest: DownloadUsageRequest): String = s"download/${
    MD5.hash(List(
      downloadUsageRequest.mediaId,
      downloadUsageRequest.metadata.downloadedBy,
      downloadUsageRequest.dateAdded.getMillis.toString
    ).mkString("_"))
  }"

  def buildId(childUsageRequest: ChildUsageRequest): String = s"child/${
    MD5.hash(List(
      childUsageRequest.mediaId,
      childUsageRequest.childMediaId,
      childUsageRequest.metadata.addedBy,
      childUsageRequest.dateAdded.getMillis.toString,
      childUsageRequest.status
    ).mkString("_"))
  }"

  def buildFromPrintUsageRecords(printUsageRecords: List[PrintUsageRecord]) =
    printUsageRecords.map(printUsageRecord => {
      val usageId = UsageIdBuilder.build(printUsageRecord)

      UsageGroup(
        Set(MediaUsageBuilder.build(printUsageRecord, usageId, buildId(printUsageRecord))),
        usageId.toString,
        printUsageRecord.dateAdded
      )
    })

  def buildFromDigitalMediaUsageRecords(digitalMediaUsageRecords: List[DigitalMediaUsageRecord]): Seq[UsageGroup] =
    digitalMediaUsageRecords.map((digitalMediaUsageRecord: DigitalMediaUsageRecord) => {
      val usageId = UsageIdBuilder.build(digitalMediaUsageRecord)
      UsageGroup(
        Set(MediaUsageBuilder.build(digitalMediaUsageRecord, usageId, buildId(digitalMediaUsageRecord))),
        usageId.toString,
        digitalMediaUsageRecord.dateAdded
      )
    })


  def build(syndicationUsageRequest: SyndicationUsageRequest): UsageGroup = {
    val usageGroupId = buildId(syndicationUsageRequest)
    UsageGroup(
      Set(MediaUsageBuilder.build(syndicationUsageRequest, usageGroupId)),
      usageGroupId,
      syndicationUsageRequest.dateAdded
    )
  }

  def build(frontUsageRequest: FrontUsageRequest): UsageGroup = {
    val usageGroupId = buildId(frontUsageRequest)
    UsageGroup(
      Set(MediaUsageBuilder.build(frontUsageRequest, usageGroupId)),
      usageGroupId,
      frontUsageRequest.dateAdded
    )
  }

  def build(downloadUsageRequest: DownloadUsageRequest): UsageGroup = {
    val usageGroupId = buildId(downloadUsageRequest)
    UsageGroup(
      Set(MediaUsageBuilder.build(downloadUsageRequest, usageGroupId)),
      usageGroupId,
      downloadUsageRequest.dateAdded
    )
  }

  def build(childUsageRequest: ChildUsageRequest): UsageGroup = {
    val usageGroupId = buildId(childUsageRequest)
    UsageGroup(
      Set(MediaUsageBuilder.build(childUsageRequest, usageGroupId)),
      usageGroupId,
      childUsageRequest.dateAdded
    )
  }

  private def createUsagesLogging(usage: MediaUsage)(implicit logMarker: LogMarker) = {
    logger.info(logMarker, s"Built MediaUsage for ${usage.mediaId}")

    usage.digitalUsageMetadata.foreach(meta => {
      logger.info(logMarker, s"Digital MediaUsage for ${usage.mediaId}: ${Json.toJson(meta)}")
    })

    usage.printUsageMetadata.foreach(meta => {
      logger.info(logMarker, s"Print MediaUsage for ${usage.mediaId}: ${Json.toJson(meta)}")
    })
    usage
  }

}

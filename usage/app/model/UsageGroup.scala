package model

import play.api.libs.json._
import com.gu.contentapi.client.model.v1.{Content, Element, ElementType}
import com.gu.contentatom.thrift.{Atom, AtomData}
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.model.usage.{DigitalUsageMetadata, MediaUsage, PublishedUsageStatus, UsageStatus}
import lib.{ContentHelpers, MD5, MediaUsageBuilder, UsageConfig}
import org.joda.time.DateTime

case class UsageGroup(
  usages: Set[MediaUsage],
  grouping: String,
  lastModified: DateTime,
  isReindex: Boolean = false,
  maybeStatus: Option[UsageStatus] = None
)
class UsageGroupOps(config: UsageConfig)
  extends GridLogging {

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

  def build(printUsageRecords: List[PrintUsageRecord]) =
    printUsageRecords.map(printUsageRecord => {
      val usageId = UsageIdBuilder.build(printUsageRecord)

      UsageGroup(
        Set(MediaUsageBuilder.build(printUsageRecord, usageId, buildId(printUsageRecord))),
        usageId.toString,
        printUsageRecord.dateAdded
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

  private def isNewContent(content: Content, usageStatus: UsageStatus): Boolean = {
    val dateLimit = new DateTime(config.usageDateLimit)
    val contentFirstPublished = ContentHelpers.getContentFirstPublished(content)
    usageStatus match {
      case PublishedUsageStatus => contentFirstPublished.exists(_.isAfter(dateLimit))
      case _ => true
    }
  }

  private def filterOutAtomsWithNoImage(atoms: Seq[Atom]): Seq[Atom] = {
    for {
      atom <- atoms
      atomId = getImageId(atom)
      if atomId.isDefined
    } yield atom
  }

  private def getImageId(atom: Atom): Option[String] = {
    try {
      val posterImage = atom.data.asInstanceOf[AtomData.Media].media.posterImage
      posterImage match {
        case Some(image) => Some(image.mediaId.replace(s"${config.apiUri}/images/", ""))
        case _ => None
      }
    } catch {
      case e: ClassCastException => None
    }
  }

  private def extractCartoonUniqueMediaIds(content: Content): Set[String] =
    (for {
      elements <- content.elements.toSeq
      cartoonElement <- elements.filter(_.`type` == ElementType.Cartoon)
      asset <- cartoonElement.assets.toSeq
      data <- asset.typeData.toSeq
      cartoonVariants <- data.cartoonVariants.toSeq
      cartoonVariant <- cartoonVariants
      image <- cartoonVariant.images
      mediaId <- image.mediaId
    } yield mediaId).toSet

  private def extractImageElements(
    content: Content, usageStatus: UsageStatus, isReindex: Boolean
  )(implicit logMarker: LogMarker): Seq[Element] = {
    val isNew = isNewContent(content, usageStatus)
    val shouldRecordUsages = isNew || isReindex

    if (shouldRecordUsages) {
      logger.info(logMarker, s"Passed shouldRecordUsages")
      val groupedElements = groupImageElements(content)

      if (groupedElements.isEmpty) {
        logger.info(logMarker, s"No Matching elements found")
      } else {
        groupedElements.foreach(elements => {
          logger.info(logMarker, s"${elements.length} elements found")
          elements.foreach(element => logger.info(logMarker, s"Matching element ${element.id} found"))
        })
      }

      groupedElements.getOrElse(Seq.empty)
    } else {
      logger.info(logMarker, s"Failed shouldRecordUsages: isNew-$isNew isReindex-$isReindex")
      Seq.empty
    }
  }

  private def groupImageElements(content: Content): Option[Seq[Element]] = {
    content.elements.map(elements => {
      elements.filter(_.`type` == ElementType.Image)
        .groupBy(_.id)
        .map(_._2.head).to(collection.immutable.Seq)
    })
  }
}

case class MediaWrapper(
    mediaId: String,
    usageGroupId: String,
    contentStatus: UsageStatus,
    usageMetadata: DigitalUsageMetadata,
    lastModified: DateTime)

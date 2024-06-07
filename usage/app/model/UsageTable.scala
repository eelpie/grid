package model

import com.gu.mediaservice.lib.aws.DynamoDB
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.lib.usage.ItemToMediaUsage
import com.gu.mediaservice.model.usage.{MediaUsage, PendingUsageStatus, PublishedUsageStatus, UsageTableFullKey}
import lib.{BadInputException, WithLogMarker}
import org.apache.pekko.http.impl.util.JavaMapping.Implicits.AddAsJava
import play.api.libs.json._
import rx.lang.scala.Observable
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{DeleteItemRequest, QueryRequest, UpdateItemRequest, AttributeValue => AttributeValueV2, ReturnValue => ReturnValueV2}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters.CollectionHasAsScala

class UsageTable(
                  client2: DynamoDbClient,
                  tableName: String
                ) extends DynamoDB[MediaUsage](client2, tableName) with GridLogging { // TODO Not instane aware!

  val hashKeyName = "grouping"
  val rangeKeyName = "usage_id"
  val imageIndexName = "media_id"

  def queryByUsageId(id: String): Future[Option[MediaUsage]] = Future {
    UsageTableFullKey.build(id).flatMap { tableFullKey =>
      val request = QueryRequest.builder()
        .tableName(tableName)
        .keyConditionExpression(s"#hashKeyName = :hashKey AND #rangeKeyName = :rangeKey")
        .expressionAttributeNames(Map(
          "#hashKeyName" -> hashKeyName,
          "#rangeKeyName" -> rangeKeyName
        ).asJava)
        .expressionAttributeValues(Map(
          ":hashKey" -> AttributeValueV2.fromS(tableFullKey.hashKey),
          ":rangeKey" -> AttributeValueV2.fromS(tableFullKey.rangeKey)
        ).asJava)
        .build()

      val queryResult = client2.query(request)

      queryResult.items().asScala.map(ItemToMediaUsage.transformV2).headOption
    }
  }

  def queryByImageId(id: String)(implicit logMarkerWithId: LogMarker): Future[List[MediaUsage]] = Future {

    if (id.trim.isEmpty)
      throw new BadInputException("Empty string received for image id")

    logger.info(logMarkerWithId, s"Querying usages table for $id")
    val request = QueryRequest.builder()
      .tableName(tableName)
      .indexName(imageIndexName)
      .keyConditionExpression(s"$imageIndexName = :imageId")
      .expressionAttributeValues(Map(
        ":imageId" -> AttributeValueV2.fromS(id)
      ).asJava)
      .build()

    val unsortedUsages = client2.query(request).items().asScala.map(ItemToMediaUsage.transformV2).toList

    logger.info(logMarkerWithId, s"Query of usages table for $id found ${unsortedUsages.size} results")

    val sortedByLastModifiedNewestFirst = unsortedUsages.sortBy(_.lastModified.getMillis).reverse

    hidePendingIfPublished(
      hidePendingIfRemoved(
        sortedByLastModifiedNewestFirst
      )
    )
  }

  private def hidePendingIfRemoved(usages: List[MediaUsage]): List[MediaUsage] = usages.filterNot((mediaUsage: MediaUsage) => {
    mediaUsage.status match {
      case PendingUsageStatus => mediaUsage.isRemoved
      case _ => false
    }
  })

  private def hidePendingIfPublished(usages: List[MediaUsage]): List[MediaUsage] = usages.groupBy(_.grouping).flatMap {
    case (_, groupedUsages) =>
      val publishedUsage = groupedUsages.find(_.status match {
        case PublishedUsageStatus => true
        case _ => false
      })

      if (publishedUsage.isEmpty) {
          groupedUsages.headOption
      } else {
          publishedUsage
      }
  }.toList

  def matchUsageGroup(usageGroupWithContext: WithLogMarker[UsageGroup]): Observable[WithLogMarker[Set[MediaUsage]]] = {
    implicit val logMarker: LogMarker = usageGroupWithContext.logMarker
    val usageGroup = usageGroupWithContext.value

    logger.info(logMarker, s"Trying to match UsageGroup: ${usageGroup.grouping}")

    Observable.from(Future {
      val grouping = usageGroup.grouping

      logger.info(logMarker, s"Querying table for $grouping")
      val request = QueryRequest.builder()
        .tableName(tableName)
        .consistentRead(true)
        .keyConditionExpression("#hashKeyName = :hashKey")
        .expressionAttributeNames(Map(
          "#hashKeyName" -> hashKeyName
        ).asJava)
        .expressionAttributeValues(Map(
          ":hashKey" -> AttributeValueV2.fromS(grouping)
        ).asJava)
        .build()

      val usages = client2.query(request).items().asScala
        .map(ItemToMediaUsage.transformV2)
        .toSet

      logger.info(logMarker, s"Built matched UsageGroup ${usageGroup.grouping} (${usages.size})")

      WithLogMarker(usages)
    })
  }

  def create(mediaUsage: MediaUsage)(implicit logMarker: LogMarker): Observable[JsObject] =
    upsertFromRecord(UsageRecord.buildCreateRecord(mediaUsage))

  def update(mediaUsage: MediaUsage)(implicit logMarker: LogMarker): Observable[JsObject] =
    upsertFromRecord(UsageRecord.buildUpdateRecord(mediaUsage))

  def markAsRemoved(mediaUsage: MediaUsage)(implicit logMarker: LogMarker): Observable[JsObject] =
    upsertFromRecord(UsageRecord.buildMarkAsRemovedRecord(mediaUsage))

  def deleteRecord(mediaUsage: MediaUsage)(implicit logMarker: LogMarker): Unit = {
    logger.info(logMarker, s"deleting usage ${mediaUsage.usageId} for media id ${mediaUsage.mediaId}")

    val request = DeleteItemRequest.builder()
      .tableName(tableName)
      .key(Map(
        hashKeyName -> AttributeValueV2.fromS(mediaUsage.grouping),
        rangeKeyName -> AttributeValueV2.fromS(mediaUsage.usageId.toString)
      ).asJava)
      .build()

    client2.deleteItem(request)
  }

  private def upsertFromRecord(record: UsageRecord)(implicit logMarker: LogMarker): Observable[JsObject] = Observable.from(Future {

    val (expression, attrValues) = record.toUpdateExpressionV2
    val request = UpdateItemRequest.builder()
      .tableName(tableName)
      .key(Map(
        hashKeyName -> AttributeValueV2.fromS(record.hashKey),
        rangeKeyName -> AttributeValueV2.fromS(record.rangeKey)
      ).asJava)
      .updateExpression(expression)
      .expressionAttributeValues(attrValues.asJava)
      .returnValues(ReturnValueV2.ALL_NEW)
      .build()

    EnhancedDocument.fromAttributeValueMap(client2.updateItem(request).attributes())

  })
  .onErrorResumeNext(e => {
    logger.error(logMarker, s"Dynamo update fail for $record!", e)
    Observable.error(e)
  })
  .map(asJsObject)
}

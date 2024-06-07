package model

import com.gu.mediaservice.lib.AttributeValueConverterProvider
import com.gu.mediaservice.lib.aws.DynamoDB.jsonWithNullAsEmptyString
import com.gu.mediaservice.lib.logging.{GridLogging, LogMarker}
import com.gu.mediaservice.lib.usage.ItemToMediaUsage
import com.gu.mediaservice.model.Instance
import com.gu.mediaservice.model.usage.{MediaUsage, PendingUsageStatus, PublishedUsageStatus, UsageTableFullKey}
import lib.{BadInputException, WithLogMarker}
import play.api.libs.json._
import rx.lang.scala.Observable
import software.amazon.awssdk.enhanced.dynamodb._
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument
import software.amazon.awssdk.enhanced.dynamodb.model.{DeleteItemEnhancedRequest, QueryConditional, QueryEnhancedRequest}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, ReturnValue, UpdateItemRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsScala, IteratorHasAsScala, MapHasAsJava}

// TODO Not instance aware!
class UsageTable(client: DynamoDbClient, tableName: String) extends GridLogging {

  val hashKeyName = "grouping"
  val rangeKeyName = "usage_id"
  val imageIndexName = "media_id"


  lazy val dynamo: DynamoDbEnhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build()
  lazy val tableSchema = TableSchema.documentSchemaBuilder()
    .addIndexPartitionKey(TableMetadata.primaryIndexName(), hashKeyName, AttributeValueType.S)
    .addIndexSortKey(TableMetadata.primaryIndexName(), rangeKeyName, AttributeValueType.S)
    .addIndexPartitionKey(imageIndexName, "instance", AttributeValueType.S)
    .addIndexSortKey(imageIndexName, imageIndexName, AttributeValueType.S)
    .attributeConverterProviders(AttributeValueConverterProvider, AttributeConverterProvider.defaultProvider())
    .build()
  lazy val table = dynamo.table(tableName, tableSchema)

  def queryByUsageId(id: String)(implicit instance: Instance): Future[Option[MediaUsage]] = Future {
    UsageTableFullKey.build(id).flatMap((tableFullKey: UsageTableFullKey) => {

      val key = Key.builder()
        .partitionValue(instanceAwareHashKey(tableFullKey.hashKey))
        .sortValue(tableFullKey.rangeKey)
        .build()
      val queryResult = table.query(QueryConditional.keyEqualTo(key))
      queryResult.items().asScala.map(ItemToMediaUsage.transform).map(unwindInstanceAwareHashkey).headOption
    })
  }

  def queryByImageId(id: String)(implicit logMarkerWithId: LogMarker, instance: Instance): Future[List[MediaUsage]] = Future {

    if (id.trim.isEmpty)
      throw new BadInputException("Empty string received for image id")

    logger.info(logMarkerWithId, s"Querying usages table for $id")

    val key = Key.builder()
      .partitionValue(instance.id)
      .sortValue(id)
      .build()

    val queryResult = table.index(imageIndexName).query(QueryConditional.keyEqualTo(key))

    val unsortedUsages = queryResult.iterator()
      .asScala
      .flatMap(_.items().asScala)
      .map(ItemToMediaUsage.transform)
      .map(unwindInstanceAwareHashkey)
      .toList

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

  def matchUsageGroup(usageGroupWithContext: WithLogMarker[(UsageGroup, Instance)]): Observable[WithLogMarker[Set[MediaUsage]]] = {
    implicit val logMarker: LogMarker = usageGroupWithContext.logMarker
    val usageGroup = usageGroupWithContext.value._1
    implicit val instance: Instance = usageGroupWithContext.value._2

    logger.info(logMarker, s"Trying to match UsageGroup: ${usageGroup.grouping}")

    Observable.from(Future {
      val grouping = usageGroup.grouping

      logger.info(logMarker, s"Querying table for $grouping")
      val key = Key.builder()
        .partitionValue(instanceAwareHashKey(grouping))
        .build()
      val request =
        QueryEnhancedRequest.builder()
          .queryConditional(QueryConditional.keyEqualTo(key))
          .consistentRead(true)
          .build()
      val queryResult = table.query(request)

      val usages = queryResult.items().asScala
        .map(ItemToMediaUsage.transform)
        .map(unwindInstanceAwareHashkey)
        .toSet

      logger.info(logMarker, s"Built matched UsageGroup ${usageGroup.grouping} (${usages.size})")

      WithLogMarker(usages)
    })
  }

  def create(mediaUsage: MediaUsage)(implicit logMarker: LogMarker, instance: Instance): Observable[JsObject] =
    upsertFromRecord(UsageRecord.buildCreateRecord(mediaUsage))

  def update(mediaUsage: MediaUsage)(implicit logMarker: LogMarker, instance: Instance): Observable[JsObject] =
    upsertFromRecord(UsageRecord.buildUpdateRecord(mediaUsage))

  def markAsRemoved(mediaUsage: MediaUsage)(implicit logMarker: LogMarker, instance: Instance): Observable[JsObject] =
    upsertFromRecord(UsageRecord.buildMarkAsRemovedRecord(mediaUsage))

  def deleteRecord(mediaUsage: MediaUsage)(implicit logMarker: LogMarker, instance: Instance): EnhancedDocument = {
    logger.info(logMarker, s"deleting usage ${mediaUsage.usageId} for media id ${mediaUsage.mediaId}")

    val key = Key.builder()
      .partitionValue(instanceAwareHashKey(mediaUsage.grouping))
      .sortValue(mediaUsage.usageId.toString)
      .build()

    table.deleteItem(DeleteItemEnhancedRequest.builder().key(key).build())
  }

  private def upsertFromRecord(record: UsageRecord)(implicit logMarker: LogMarker, instance: Instance): Observable[JsObject] = Observable.from(Future {
      val key = Map(
        hashKeyName -> AttributeValue.builder().s(instanceAwareHashKey(record.hashKey)).build(),
        rangeKeyName -> AttributeValue.builder().s(record.rangeKey).build()
      ).asJava

      val request = UpdateItemRequest.builder()
        .tableName(table.tableName())
        .key(key)
        .updateExpression(record.toExpression.expression())
        .expressionAttributeNames(record.toExpression.expressionNames())
        .expressionAttributeValues(record.toExpression.expressionValues())
        .returnValues(ReturnValue.ALL_NEW)
        .build()

      client.updateItem(request)

    })
    .onErrorResumeNext(e => {
      logger.error(logMarker, s"Dynamo update fail for $record!", e)
      Observable.error(e)
    })
    .map(updateResponse => {
      val doc = EnhancedDocument.fromAttributeValueMap(updateResponse.attributes())
      jsonWithNullAsEmptyString(Json.parse(doc.toJson)).as[JsObject]
    })

  private def unwindInstanceAwareHashkey(mediaUsage: MediaUsage)(implicit instance: Instance): MediaUsage = {
    mediaUsage.copy(grouping = mediaUsage.grouping.drop(instance.id.length + 1))
  }
  private def instanceAwareHashKey(record: UsageRecord)(implicit instance: Instance) = {
    instance.id + "/" + record.hashKey
  }

  private def instanceAwareHashKey(hashKey: String)(implicit instance: Instance) = {
    instance.id + "/" + hashKey
  }
}

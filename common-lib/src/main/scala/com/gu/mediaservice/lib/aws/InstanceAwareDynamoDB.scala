package com.gu.mediaservice.lib.aws

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.document.spec._
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.document.{DynamoDB => AwsDynamoDB, _}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, KeysAndAttributes, ReturnValue}
import com.gu.mediaservice.lib.aws.DynamoDB.deleteExpr
import com.gu.mediaservice.lib.logging.GridLogging
import com.gu.mediaservice.model.Instance
import org.joda.time.DateTime
import play.api.libs.json._
import software.amazon.awssdk.enhanced.dynamodb._
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{UpdateItemRequest, AttributeValue => AttributeValueV2, ReturnValue => ReturnValueV2}

import java.util
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/**
  * A lightweight wrapper around AWS dynamo SDK for undertaking various operations
  * @param client AmazonDynamoDBAsync client
  * @param client2 DynamoDbClient client
  * @param tableName the table name for this instance of the dynamoDB wrapper
  * @param lastModifiedKey if set to a string the wrapper will maintain a last modified with that name on any update
  * @tparam T The type of this table
  */
class InstanceAwareDynamoDB[T](client: AmazonDynamoDBAsync, client2: DynamoDbClient, tableName: String, lastModifiedKey: Option[String] = None) extends GridLogging {
  lazy val dynamo2: DynamoDbEnhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(client2).build()
  lazy val tableSchema = TableSchema.documentSchemaBuilder()
    .addIndexPartitionKey(TableMetadata.primaryIndexName(), InstanceKey, AttributeValueType.S)
    .addIndexSortKey(TableMetadata.primaryIndexName(), IdKey, AttributeValueType.S)
    .attributeConverterProviders(AttributeConverterProvider.defaultProvider())
    .build()
  lazy val table2 = dynamo2.table(tableName, tableSchema)

  lazy val dynamo = new AwsDynamoDB(client)
  lazy val table: Table = dynamo.getTable(tableName)

  private val IdKey = "id"
  private val InstanceKey = "instance"

  private def itemKey(id: String)(implicit instance: Instance) = {
    Key.builder().partitionValue(instance.id).sortValue(id).build()
  }

  def getV2(id: String)(implicit ex: ExecutionContext, instance: Instance): Future[JsObject] = Future {
    table2.getItem(itemKey(id))
  } flatMap docOrNotFound map asJsObject

  private def getV2(id: String, attribute: String)(implicit ex: ExecutionContext, instance: Instance): Future[EnhancedDocument] = Future {
    Option(table2.getItem(itemKey(id))).flatMap(doc => Option.when(doc.isPresent(attribute))(doc))
  } flatMap {
    case Some(doc) => Future.successful(doc)
    case None => Future.failed(NoItemFound)
  }

  private def docOrNotFound(docOrNull: EnhancedDocument): Future[EnhancedDocument] = {
    Option(docOrNull) match {
      case Some(doc) => Future.successful(doc)
      case None       => Future.failed(NoItemFound)
    }
  }

  def removeKey(id: String, key: String)
               (implicit ex: ExecutionContext, instance: Instance): Future[JsObject] =
    update(
      id,
      s"REMOVE $key"
    )

  def removeKeyV2(id: String, key: String)(implicit ex: ExecutionContext, instance: Instance) = Future{
    updateV2(id, DynamoDB.removeExpr(key, lastModifiedKey))
  }

  def deleteItemV2(id: String)(implicit ex: ExecutionContext, instance: Instance): Future[Unit] = Future {
    table2.deleteItem(
      itemKey(id)
    )
  }
  def booleanGetV2(id: String, key: String)
    (implicit ex: ExecutionContext, instance: Instance): Future[Boolean] = {
      getV2(id, key).map(_.getBoolean(key).booleanValue())
  }

  def booleanSetV2(id: String, key: String, value: Boolean)
                (implicit ex: ExecutionContext, instance: Instance): Future[JsObject] = Future {
    updateV2(
      id,
      DynamoDB.setExpr(key, lastModifiedKey),
      AttributeValueV2.fromBool(value)
    )
  }

  def booleanSetOrRemoveV2(id: String, key: String, value: Boolean)
                        (implicit ex: ExecutionContext, instance: Instance): Future[JsObject] =
    if (value) booleanSetV2(id, key, value)
    else removeKeyV2(id, key)

  def stringSetV2(id: String, key: String, value: String)(implicit ex: ExecutionContext, instance: Instance): Future[JsObject] = Future {
    updateV2(id,  DynamoDB.setExpr(key, lastModifiedKey), AttributeValueV2.fromS(value))
  }

  def setGetV2(id: String, key: String)
    (implicit ex: ExecutionContext, instance: Instance): Future[Set[String]] = {
      getV2(id, key).map(_.getStringSet(key).asScala.toSet)
  }

  def setAddV2(id: String, key: String, value: List[String])(implicit ex: ExecutionContext, instance: Instance): Future[JsObject] = Future {
    updateV2(id, DynamoDB.addExpr(key, lastModifiedKey), AttributeValueV2.fromSs(value.asJava))
  }
  def batchGet(ids: List[String], attributeKey: String)
              (implicit ex: ExecutionContext, rjs: Reads[T], instance: Instance): Future[Map[String, T]] = {
    val keyChunkList = ids
      .map(k => Map(InstanceKey -> new AttributeValue(instance.id), IdKey -> new AttributeValue(k)).asJava)
      .grouped(100)

    Future.traverse(keyChunkList) { keyChunk => {
      val keysAndAttributes: KeysAndAttributes = new KeysAndAttributes().withKeys(keyChunk.asJava)

      @tailrec
      def nextPageOfBatch(request: java.util.Map[String, KeysAndAttributes], acc: List[(String, T)])
                         (implicit ex: ExecutionContext, rjs: Reads[T]): List[(String, T)] = {
        if (request.isEmpty) acc
        else {
          logger.info(s"Fetching records for $request")
          val response = client.batchGetItem(request)
          val responses = response.getResponses
          logger.info(s"Got responses of $responses")
          val results = responses.get(tableName).asScala.toList
            .flatMap(att => {
              val attributes: util.Map[String, AnyRef] = ItemUtils.toSimpleMapValue(att)
              logger.info(s"Obtained attributes of $attributes from response $att")
              val json = asJsObject(Item.fromMap(attributes))
              val maybeT = (json \ attributeKey).asOpt[T]
              logger.info(s"Obtained a T of $maybeT from json $json")
              maybeT.map(
                attributes.get(IdKey).toString -> _
              )
            })
          logger.info(s"Got $results for request")
          nextPageOfBatch(response.getUnprocessedKeys, acc ::: results)
        }
      }

      Future {
        nextPageOfBatch(Map(tableName -> keysAndAttributes).asJava, Nil).toMap
      }
    }}
      .map(chunkIterator => chunkIterator.fold(Map.empty)((acc, result) => acc ++ result))
  }


  // We cannot update, so make sure you send over the WHOLE document
  def jsonAddV2(id: String, key: String, value: Map[String, JsValue])
             (implicit ex: ExecutionContext, instance: Instance): Future[JsObject] = Future {
    updateV2(
      id,
      setExpr(key, lastModifiedKey),
      AttributeValueV2.fromM(value.view.mapValues(DynamoDB.jsonToAttributeValue).toMap.asJava)
    )
  }

  def setDeleteV2(id: String, key: String, value: String)
                 (implicit ex: ExecutionContext, instance: Instance): Future[JsObject] = Future {
    updateV2(id,  deleteExpr(key, lastModifiedKey), AttributeValueV2.fromSs(List(value).asJava))
  }

  def scanForId(indexName: String, keyname: String, key: String)(implicit ex: ExecutionContext, instance: Instance) = Future {
    val index = table.getIndex(indexName)

    val spec = new QuerySpec()
      .withKeyConditionExpression(s"instance = :instance AND $keyname = :key")
      .withValueMap(new ValueMap()
        .withString(":instance", instance.id)
        .withString(":key", key))

    val items: List[Item] = index.query(spec).iterator.asScala.toList
    items map (a => a.getString("id"))
  }

  private def updateRequestBuilder(id: String, expression: String)(implicit instance: Instance) = {
    UpdateItemRequest.builder()
      .key(Map(
        InstanceKey -> AttributeValueV2.fromS(instance.id),
        IdKey -> AttributeValueV2.fromS(id)).asJava
      )
      .updateExpression(expression)
      .returnValues(ReturnValueV2.ALL_NEW)
      .tableName(tableName)
  }

  def updateV2(id: String, expression: String, attribute: AttributeValueV2)(implicit instance: Instance) = {
    val baseValuesMap = Map(":value" -> attribute)
    val valuesMap = lastModifiedKey.fold(baseValuesMap)(key => baseValuesMap ++ Map(s":${key}" -> AttributeValueV2.fromS(DateTime.now().toString)))
    val updateRequest = updateRequestBuilder(id, expression)
      .expressionAttributeValues(valuesMap.asJava)
      .build()
    val updateItemResponse = client2.updateItem(updateRequest)
    val jsonString = EnhancedDocument.fromAttributeValueMap(updateItemResponse.attributes()).toJson
    Json.parse(jsonString).as[JsObject]
  }

  def updateV2(id: String, expression: String)(implicit instance: Instance) = {
    val valuesMap = lastModifiedKey.fold(Map.empty[String, AttributeValueV2])(key =>  Map(s":${key}" -> AttributeValueV2.fromS(DateTime.now().toString)))
    val updateRequest = updateRequestBuilder(id, expression)
      .expressionAttributeValues(valuesMap.asJava)
      .build()
    val updateItemResponse = client2.updateItem(updateRequest)
    val jsonString = EnhancedDocument.fromAttributeValueMap(updateItemResponse.attributes()).toJson
    Json.parse(jsonString).as[JsObject]
  }

  def update(id: String, expression: String, valueMap: ValueMap)
            (implicit ex: ExecutionContext, instance: Instance): Future[JsObject] =
    update(id, expression, Some(valueMap))

  def update(id: String, expression: String, valueMap: Option[ValueMap] = None)
            (implicit ex: ExecutionContext, instance: Instance): Future[JsObject] = Future {

    val baseUpdateSpec = new UpdateItemSpec().
      withPrimaryKey(IdKey, id, InstanceKey, instance.id).
      withUpdateExpression(expression).
      withReturnValues(ReturnValue.ALL_NEW).
      withValueMap(valueMap.orNull)

    val updateSpec = lastModifiedKey.map { key =>
      DynamoDB.addLastModifiedUpdate(baseUpdateSpec, key, DateTime.now)
    }.getOrElse(baseUpdateSpec)

    table.updateItem(updateSpec)
  } map asJsObject


  // FIXME: surely there must be a better way to convert?
  def asJsObject(item: Item): JsObject =
    jsonWithNullAsEmptyString(Json.parse(item.toJSON)).as[JsObject] - IdKey - InstanceKey

  def asJsObject(doc: EnhancedDocument): JsObject =
    jsonWithNullAsEmptyString(Json.parse(doc.toJson)).as[JsObject] - IdKey - InstanceKey

  def asJsObject(outcome: UpdateItemOutcome): JsObject =
    Option(outcome.getItem) map asJsObject getOrElse Json.obj()

  // FIXME: Dynamo accepts `null`, but not `""`. This is a well documented issue
  // around the community. This guard keeps the introduction of `null` fairly
  // fenced in this Dynamo play area. `null` is continual and big annoyance with AWS libs.
  // see: https://forums.aws.amazon.com/message.jspa?messageID=389032
  // see: http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DataModel.html
  def mapJsValue(jsValue: JsValue)(f: JsValue => JsValue): JsValue = jsValue match {
    case JsObject(items) => JsObject(items.map{ case (k, v) => k -> mapJsValue(v)(f) })
    case JsArray(items) => JsArray(items.map(f))
    case value => f(value)
  }

  def jsonWithNullAsEmptyString(jsValue: JsValue): JsValue = mapJsValue(jsValue) {
    case JsNull => JsString("")
    case value => value
  }

  def setExpr[T](key: String, lastModifiedKey: Option[String]) = {
    val baseExpression = s"SET $key = :value"
    lastModifiedKey.fold(baseExpression)(lastModifiedKey => s"$baseExpression, $lastModifiedKey = :$lastModifiedKey")
  }

}

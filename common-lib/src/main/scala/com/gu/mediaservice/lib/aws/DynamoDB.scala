package com.gu.mediaservice.lib.aws

import com.gu.mediaservice.lib.logging.GridLogging
import org.joda.time.DateTime
import play.api.libs.json._
import software.amazon.awssdk.enhanced.dynamodb._
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{BatchGetItemRequest, QueryRequest, UpdateItemRequest, AttributeValue => AttributeValueV2, KeysAndAttributes => KeysAndAttributesV2, ReturnValue => ReturnValueV2}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object NoItemFound extends Throwable("item not found")

/**
  * A lightweight wrapper around AWS dynamo SDK for undertaking various operations
  * @param client2 DynamoDbClient client
  * @param tableName the table name for this instance of the dynamoDB wrapper
  * @param lastModifiedKey if set to a string the wrapper will maintain a last modified with that name on any update
  * @tparam T The type of this table
  */
class DynamoDB[T](client2: DynamoDbClient, tableName: String, lastModifiedKey: Option[String] = None) extends GridLogging {
  lazy val dynamo2: DynamoDbEnhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(client2).build()
  lazy val tableSchema = TableSchema.documentSchemaBuilder()
    .addIndexPartitionKey(TableMetadata.primaryIndexName(), IdKey, AttributeValueType.S)
    .attributeConverterProviders(AttributeConverterProvider.defaultProvider())
    .build()
  lazy val table2 = dynamo2.table(tableName, tableSchema)

  private val IdKey = "id"

  private def itemKey(key: String) = Key.builder().partitionValue(key).build()

  def getV2(id: String)(implicit ex: ExecutionContext): Future[JsObject] = Future {
    table2.getItem(itemKey(id))
  } flatMap docOrNotFound map asJsObject

  private def getV2(id: String, attribute: String)(implicit ex: ExecutionContext): Future[EnhancedDocument] = Future {
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

  def removeKeyV2(id: String, key: String)(implicit ex: ExecutionContext) = Future{
    updateV2(id, DynamoDB.removeExpr(key, lastModifiedKey))
  }

  def deleteItemV2(id: String)(implicit ex: ExecutionContext): Future[Unit] = Future {
    table2.deleteItem(
      Key.builder().partitionValue(id).build()
    )
  }
  def booleanGetV2(id: String, key: String)
    (implicit ex: ExecutionContext): Future[Boolean] = {
      getV2(id, key).map(_.getBoolean(key).booleanValue())
  }

  def booleanSetV2(id: String, key: String, value: Boolean)
                (implicit ex: ExecutionContext): Future[JsObject] = Future {
    updateV2(
      id,
      DynamoDB.setExpr(key, lastModifiedKey),
      AttributeValueV2.fromBool(value)
    )
  }

  def booleanSetOrRemoveV2(id: String, key: String, value: Boolean)
                        (implicit ex: ExecutionContext): Future[JsObject] =
    if (value) booleanSetV2(id, key, value)
    else removeKeyV2(id, key)

  def stringSetV2(id: String, key: String, value: String)(implicit ex: ExecutionContext): Future[JsObject] = Future {
    updateV2(id,  DynamoDB.setExpr(key, lastModifiedKey), AttributeValueV2.fromS(value))
  }

  def setGetV2(id: String, key: String)
    (implicit ex: ExecutionContext): Future[Set[String]] = {
    getV2(id, key).map(_.getStringSet(key).asScala.toSet)
  }

  def setAddV2(id: String, key: String, value: List[String])(implicit ex: ExecutionContext): Future[JsObject] = Future {
    updateV2(id, DynamoDB.addExpr(key, lastModifiedKey), AttributeValueV2.fromSs(value.asJava))
  }

  def batchGetV2(ids: List[String], attributeKey: String)
                (implicit ex: ExecutionContext, rjs: Reads[T]): Future[Map[String, T]] = {
    val keyChunkList = ids
      .map(k => Map(IdKey -> AttributeValueV2.fromS(k)).asJava)
      .grouped(100)

    Future.traverse(keyChunkList) { keyChunk => {
        val keysAndAttributes: KeysAndAttributesV2 = KeysAndAttributesV2.builder().keys(keyChunk.asJava).build()

        @tailrec
        def nextPageOfBatch(request: java.util.Map[String, KeysAndAttributesV2], acc: List[(String, T)])
                           (implicit ex: ExecutionContext, rjs: Reads[T]): List[(String, T)] = {
          if (request.isEmpty) acc
          else {
            logger.info(s"Fetching records for $request")
            val response = client2.batchGetItem(BatchGetItemRequest.builder().requestItems(request).build())
            val responses = response.responses()
            logger.info(s"Got responses of $responses")
            val results = responses.get(tableName).asScala.toList
              .flatMap(att => {
                logger.info(s"Obtained attributes of $att from response")
                val json = asJsObject(EnhancedDocument.fromAttributeValueMap(att))
                val maybeT = (json \ attributeKey).asOpt[T]
                logger.info(s"Obtained a T of $maybeT from json $json")
                maybeT.map(
                  att.get(IdKey).s() -> _
                )
              })
            logger.info(s"Got $results for request")
            nextPageOfBatch(response.unprocessedKeys(), acc ::: results)
          }
        }

        Future {
          nextPageOfBatch(Map(tableName -> keysAndAttributes).asJava, Nil).toMap
        }
      }
      }
      .map(chunkIterator => chunkIterator.fold(Map.empty)((acc, result) => acc ++ result))
  }

  // We cannot update, so make sure you send over the WHOLE document
  def jsonAddV2(id: String, key: String, value: Map[String, JsValue])
             (implicit ex: ExecutionContext): Future[JsObject] = Future {
    updateV2(
      id,
      DynamoDB.setExpr(key, lastModifiedKey),
      AttributeValueV2.fromM(value.view.mapValues(DynamoDB.jsonToAttributeValue).toMap.asJava)
    )
  }

  def setDeleteV2(id: String, key: String, value: String)
               (implicit ex: ExecutionContext): Future[JsObject] = Future {
    updateV2(id,  DynamoDB.deleteExpr(key, lastModifiedKey), AttributeValueV2.fromSs(List(value).asJava))
  }

  def scanForIdV2(indexName: String, keyname: String, key: String)(implicit ex: ExecutionContext): Future[List[String]] = Future {
    val request = QueryRequest.builder()
      .tableName(tableName)
      .indexName(indexName)
      .keyConditionExpression(s"$keyname = :key")
      .expressionAttributeValues(Map(":key" -> AttributeValueV2.fromS(key)).asJava)
      .build()

    client2.query(request).items().asScala.toList
      .flatMap(item => Option(item.get("id")).map(_.s()))
  }

  private def updateRequestBuilder(id: String, expression: String) = {
    UpdateItemRequest.builder()
      .key(Map(IdKey -> AttributeValueV2.fromS(id)).asJava)
      .updateExpression(expression)
      .returnValues(ReturnValueV2.ALL_NEW)
      .tableName(tableName)
  }

  private def updateV2(id: String, expression: String, attribute: AttributeValueV2): JsObject = {
    updateV2(id, expression, Map(":value" -> attribute))
  }

  private def updateV2(id: String, expression: String): JsObject = {
    updateV2(id, expression, Map.empty[String, AttributeValueV2])
  }

  private def updateV2(id: String, expression: String, baseValuesMap: Map[String, AttributeValueV2]) = {
    val valuesMap = lastModifiedKey.fold(baseValuesMap)(key => baseValuesMap ++ Map(s":${key}" -> AttributeValueV2.fromS(DateTime.now().toString)))
    val updateRequest = updateRequestBuilder(id, expression)
      .expressionAttributeValues(valuesMap.asJava)
      .build()
    val updateItemResponse = client2.updateItem(updateRequest)
    val jsonString = EnhancedDocument.fromAttributeValueMap(updateItemResponse.attributes()).toJson
    Json.parse(jsonString).as[JsObject]
  }

  def asJsObject(doc: EnhancedDocument): JsObject =
    jsonWithNullAsEmptyString(Json.parse(doc.toJson)).as[JsObject] - IdKey

  // FIXME: Dynamo accepts `null`, but not `""`. This is a well documented issue
  // around the community. This guard keeps the introduction of `null` fairly
  // fenced in this Dynamo play area. `null` is continual and big annoyance with AWS libs.
  // see: https://forums.aws.amazon.com/message.jspa?messageID=389032
  // see: http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DataModel.html
  private def mapJsValue(jsValue: JsValue)(f: JsValue => JsValue): JsValue = jsValue match {
    case JsObject(items) => JsObject(items.map{ case (k, v) => k -> mapJsValue(v)(f) })
    case JsArray(items) => JsArray(items.map(f))
    case value => f(value)
  }

  private def jsonWithNullAsEmptyString(jsValue: JsValue): JsValue = mapJsValue(jsValue) {
    case JsNull => JsString("")
    case value => value
  }

}

object DynamoDB {
  def jsonToAttributeValue(json: JsValue): AttributeValueV2 = {
    json match {
      case JsString(v)  => AttributeValueV2.fromS(v)
      case JsBoolean(b) => AttributeValueV2.fromBool(b)
      case JsTrue => AttributeValueV2.fromBool(true)
      case JsFalse => AttributeValueV2.fromBool(false)
      case JsNumber(n)  => AttributeValueV2.fromN(n.toString())
      case JsNull => AttributeValueV2.fromNul(true)
      case JsObject(obj)  => AttributeValueV2.fromM(obj.view.mapValues(s => jsonToAttributeValue(s)).toMap.asJava)
      case JsArray(arr)   => AttributeValueV2.fromL(arr.toList.map(jsonToAttributeValue).asJava)
    }
  }

  def caseClassToMap[T](caseClass: T)(implicit tjs: Writes[T]): Map[String, JsValue] =
    Json.toJson[T](caseClass).as[JsObject].as[Map[String, JsValue]]

  def setExpr[T](key: String, lastModifiedKey: Option[String]) = {
    val baseExpression = s"SET $key = :value"
    lastModifiedKey.fold(baseExpression)(lastModifiedKey => s"$baseExpression, $lastModifiedKey = :$lastModifiedKey")
  }

  def removeExpr(key: String, lastModifiedKey: Option[String]) = {
    generateExpression(s"REMOVE $key", lastModifiedKey)
  }

  def addExpr(key: String, lastModifiedKey: Option[String]) = {
    generateExpression(s"ADD $key :value", lastModifiedKey)
  }

  def deleteExpr(key: String, lastModifiedKey: Option[String]) = {
    generateExpression(s"DELETE $key :value", lastModifiedKey)
  }

  def generateExpression(baseExpression: String, lastModifiedKey: Option[String]) = {
    lastModifiedKey.fold(baseExpression)(lastModifiedKey => s"$baseExpression SET $lastModifiedKey = :$lastModifiedKey")
  }
}

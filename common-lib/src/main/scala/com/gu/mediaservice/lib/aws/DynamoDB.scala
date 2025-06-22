package com.gu.mediaservice.lib.aws

import java.util

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.dynamodbv2.document.spec.{DeleteItemSpec, GetItemSpec, PutItemSpec, QuerySpec, UpdateItemSpec}
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.document.{DynamoDB => AwsDynamoDB, _}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, KeysAndAttributes, ReturnValue}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.logging.GridLogging
import org.joda.time.DateTime
import play.api.libs.json._

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

object NoItemFound extends Throwable("item not found")

/**
  * A lightweight wrapper around AWS dynamo SDK for undertaking various operations
  * @param config Common grid config including AWS credentials
  * @param tableName the table name for this instance of the dynamoDB wrapper
  * @param lastModifiedKey if set to a string the wrapper will maintain a last modified with that name on any update
  * @tparam T The type of this table
  */
class DynamoDB[T](config: CommonConfig, tableName: String, lastModifiedKey: Option[String] = None) extends GridLogging {
  lazy val client: AmazonDynamoDBAsync = config.withAWSCredentials(AmazonDynamoDBAsyncClientBuilder.standard()).build()
  lazy val dynamo = new AwsDynamoDB(client)
  lazy val table: Table = dynamo.getTable(tableName)

  val IdKey = "id"

  def exists(id: String)(implicit ex: ExecutionContext): Future[Boolean] = Future {
      table.getItem(new GetItemSpec().withPrimaryKey(IdKey, id))
  } map(Option(_).isDefined)

  def get(id: String)
         (implicit ex: ExecutionContext): Future[JsObject] = Future {
    table.getItem(
      new GetItemSpec().
        withPrimaryKey(IdKey, id)
    )
  } flatMap itemOrNotFound map asJsObject

  private def get(id: String, key: String)
         (implicit ex: ExecutionContext): Future[Item] = Future {
    table.getItem(
      new GetItemSpec()
        .withPrimaryKey(IdKey, id)
        .withAttributesToGet(key)
    )
  } flatMap itemOrNotFound

  private def itemOrNotFound(itemOrNull: Item): Future[Item] = {
    Option(itemOrNull) match {
      case Some(item) => Future.successful(item)
      case None       => Future.failed(NoItemFound)
    }
  }

  def removeKey(id: String, key: String)
               (implicit ex: ExecutionContext): Future[JsObject] =
    update(
      id,
      s"REMOVE $key"
    )

  def deleteItem(id: String)(implicit ex: ExecutionContext): Future[Unit] = Future {
    table.deleteItem(new DeleteItemSpec().withPrimaryKey(IdKey, id))
  }

  def booleanGet(id: String, key: String)
                (implicit ex: ExecutionContext): Future[Option[Boolean]] =
    // TODO: add Option to item as it can be null
    get(id, key).map{ item => item.get(key) match {
      case b: java.lang.Boolean => Some(b.booleanValue)
      case _ => None
    }}

  def booleanSet(id: String, key: String, value: Boolean)
                (implicit ex: ExecutionContext): Future[JsObject] =
    update(
      id,
      s"SET $key = :value",
      new ValueMap().withBoolean(":value", value)
    )

  def booleanSetOrRemove(id: String, key: String, value: Boolean)
                        (implicit ex: ExecutionContext): Future[JsObject] =
    if (value) booleanSet(id, key, value)
    else removeKey(id, key)

  def stringSet(id: String, key: String, value: JsValue)
                (implicit ex: ExecutionContext): Future[JsObject] =
    update(
      id,
      s"SET $key = :value",
      valueMapWithNullForEmptyString(Map(":value" -> value))
    )

  def setGet(id: String, key: String)
            (implicit ex: ExecutionContext): Future[Set[String]] =
    get(id, key).map{ item => Option(item.getStringSet(key)) match {
        case Some(set) => set.asScala.toSet
        case None      => Set()
      }
    }

  def setAdd(id: String, key: String, value: String)
            (implicit ex: ExecutionContext): Future[JsObject] =
    update(
      id,
      s"ADD $key :value",
      new ValueMap().withStringSet(":value", value)
    )

  def setAdd(id: String, key: String, value: List[String])
            (implicit ex: ExecutionContext): Future[JsObject] =
    update(
      id,
      s"ADD $key :value",
      new ValueMap().withStringSet(":value", value:_*)
    )


  def jsonGet(id: String, key: String)
             (implicit ex: ExecutionContext): Future[JsValue] =
    get(id, key).map(item => asJsObject(item))

  def batchGet(ids: List[String], attributeKey: String)
              (implicit ex: ExecutionContext, rjs: Reads[T]): Future[Map[String, T]] = {
    val keyChunkList = ids
      .map(k => Map(IdKey -> new AttributeValue(k)).asJava)
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
  def jsonAdd(id: String, key: String, value: Map[String, JsValue])
             (implicit ex: ExecutionContext): Future[JsObject] =
    update(
      id,
      s"SET $key = :value",
        new ValueMap().withMap(":value", valueMapWithNullForEmptyString(value))
    )

  def setDelete(id: String, key: String, value: String)
               (implicit ex: ExecutionContext): Future[JsObject] =
    update(
      id,
      s"DELETE $key :value",
      new ValueMap().withStringSet(":value", value)
    )

  def listGet(id: String, key: String)
                (implicit ex: ExecutionContext, reads: Reads[T]): Future[List[T]] = {

    get(id, key) map { item =>
      Option(item.toJSON) match {
        case Some(json) => (Json.parse(json) \ key).as[List[T]]
        case None      => Nil
      }
    }
  }

  def listAdd(id: String, key: String, value: T)
                (implicit ex: ExecutionContext, tjs: Writes[T], rjs: Reads[T]): Future[List[T]] = {

    // TODO: Deal with the case that we don't have JSON serialisers, for now we just fail.
    val json = Json.toJson(value).as[JsObject]
    val valueMap = DynamoDB.jsonToValueMap(json)
    def append =
      update(
        id, s"SET $key = list_append($key, :value)",
        new ValueMap().withList(":value", valueMap)
      )

    def create =
      update(
        id, s"SET $key = :value",
        new ValueMap().withList(":value", valueMap)
      )

    // DynamoDB doesn't seem to have a way of saying create the list if it doesn't exist then
    // append to it. So what we're saying here is:
    // Append to the list => if it doesn't exist => create it with the initial value.
    append.map(j => (j \ key).as[List[T]]) recoverWith {
      case err: AmazonServiceException => create.map(j => (j \ key).as[List[T]])
      case err => throw err
    }
  }

  def listRemoveIndexes(id: String, key: String, indexes: List[Int])
                          (implicit ex: ExecutionContext, rjs: Reads[T]): Future[List[T]] =
    update(
      id, s"REMOVE ${indexes.map(i => s"$key[$i]").mkString(",")}"
    ) map(j => (j \ key).as[List[T]])

  def objPut(id: String, key: String, value: T)
                 (implicit ex: ExecutionContext, wjs: Writes[T], rjs: Reads[T]): Future[T] = Future {

    val item = new Item().withPrimaryKey(IdKey, id).withJSON(key, Json.toJson(value).toString)

    val spec = new PutItemSpec().withItem(item)
    table.putItem(spec)
    // As PutItem only returns `null` if the item didn't exist, or the old item if it did,
    // all we care about is whether it completed.
  } map (_ => value)

  def scan()(implicit ex: ExecutionContext) = Future {
    table.scan().iterator.asScala.toList
  } map (_.map(asJsObject))

  def scanForId(indexName: String, keyname: String, key: String)(implicit ex: ExecutionContext) = Future {
    val index = table.getIndex(indexName)

    val spec = new QuerySpec()
      .withKeyConditionExpression(s"$keyname = :key")
      .withValueMap(new ValueMap()
        .withString(":key", key))

    val items: List[Item] = index.query(spec).iterator.asScala.toList
    items map (a => a.getString("id"))
  }

  def update(id: String, expression: String, valueMap: ValueMap)
            (implicit ex: ExecutionContext): Future[JsObject] =
    update(id, expression, Some(valueMap))

  def update(id: String, expression: String, valueMap: Option[ValueMap] = None)
            (implicit ex: ExecutionContext): Future[JsObject] = Future {

    val baseUpdateSpec = new UpdateItemSpec().
      withPrimaryKey(IdKey, id).
      withUpdateExpression(expression).
      withReturnValues(ReturnValue.ALL_NEW).
      withValueMap(valueMap.orNull)

    val updateSpec = lastModifiedKey.map { key =>
      DynamoDB.addLastModifiedUpdate(baseUpdateSpec, key, DateTime.now)
    }.getOrElse(baseUpdateSpec)

    table.updateItem(updateSpec)
  } map asJsObject


  // FIXME: surely there must be a better way to convert?
  def asJsObject(item: Item): JsObject = {
    jsonWithNullAsEmptyString(Json.parse(item.toJSON)).as[JsObject] - IdKey
  }

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

  def valueMapWithNullForEmptyString(value: Map[String, JsValue]) = {
    val valueMap = new ValueMap()
    value.map     { case(k, v) => (k, if (v == JsNull) null else v) }
         .foreach { case(k, v) => valueMap.withJSON(k, Json.stringify(v)) }

    valueMap
  }

}

object DynamoDB {
  def jsonToValueMap(json: JsObject): ValueMap = {
    val valueMap = new ValueMap()
    json.value map { case (key, value) =>
      value match {
        case v: JsString  => valueMap.withString(key, v.value)
        case v: JsBoolean => valueMap.withBoolean(key, v.value)
        case v: JsNumber  => valueMap.withNumber(key, v.value)
        case v: JsObject  => valueMap.withMap(key, jsonToValueMap(v))

        // TODO: Lists of different Types? JsArray is not type safe (because json lists aren't)
        // so this leaves us in a bit of a pickle when converting them. So for now we only support
        // List[String]
        case v: JsArray   => valueMap.withList(key, v.value.map {
          case i: JsString => i.value
          case i: JsValue => i.toString
        }.asJava)
        case _ => valueMap
      }
    }
    valueMap
  }
  def caseClassToMap[T](caseClass: T)(implicit tjs: Writes[T]): Map[String, JsValue] =
    Json.toJson[T](caseClass).as[JsObject].as[Map[String, JsValue]]

  def addLastModifiedUpdate(update: UpdateItemSpec, lastModifiedKey: String, lastModifiedDate: DateTime): UpdateItemSpec = {
    val expression = update.getUpdateExpression
    val valueMap: ValueMap = {
      val m = new ValueMap()
      Option(update.getValueMap).foreach { vm =>
        m.putAll(vm)
      }
      m
    }

    val newExpression = {
      val keyUpdate: String = s"$lastModifiedKey = :$lastModifiedKey"
      if (expression.contains("SET ")) {
          // add to existing clause
        expression.replace("SET ", s"SET ${keyUpdate}, ")
      } else {
        // add SET clause to existing expression
        s"SET $keyUpdate ${expression}"
      }
    }

    valueMap.put(s":$lastModifiedKey", lastModifiedDate.toString)

    update
      .withUpdateExpression(newExpression)
      .withValueMap(valueMap)
  }
}

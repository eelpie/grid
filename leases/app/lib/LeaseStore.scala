package lib

import com.gu.mediaservice.model.Instance
import com.gu.mediaservice.model.leases.{AllowSyndicationLease, AllowUseLease, DenySyndicationLease, DenyUseLease, MediaLease, MediaLeaseType}
import org.joda.time.DateTime
import org.scanamo._
import org.scanamo.generic.semiauto.deriveDynamoFormat
import org.scanamo.syntax._
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, PutItemRequest}

import java.util
import scala.concurrent.{ExecutionContext, Future}
import collection.JavaConverters._

class LeaseStore(config: LeasesConfig) {

  implicit val dateTimeFormat: DynamoFormat[DateTime] = {
    new DynamoFormat[DateTime] {
      // TODO hard fail but I'm done with this! Far too many snow flake DSLs in use...
      override def read(av: DynamoValue): Either[DynamoReadError, DateTime] = {
        Right(DateTime.parse(av.toAttributeValue.s()))
      }
      override def write(t: DateTime): DynamoValue = DynamoValue.fromString(t.toString)
    }
  }

  implicit val enumFormat: DynamoFormat[com.gu.mediaservice.model.leases.MediaLeaseType] = {
    new DynamoFormat[com.gu.mediaservice.model.leases.MediaLeaseType] {
      // TODO hard fail but I'm done with this! Far too many snow flake DSLs in use...
      override def read(av: DynamoValue): Either[DynamoReadError, MediaLeaseType] = {
        val name = av.toAttributeValue.s()
        val x = name match {
          case "allow-use" => AllowUseLease
          case "deny-use" => DenyUseLease
          case "allow-syndication" => AllowSyndicationLease
          case "deny-syndication" => DenySyndicationLease
        }
        Right(x)
      }
      override def write(t: MediaLeaseType): DynamoValue = DynamoValue.fromString(t.name)
    }
  }

  implicit val formatLeases: DynamoFormat[com.gu.mediaservice.model.leases.MediaLease] = deriveDynamoFormat[com.gu.mediaservice.model.leases.MediaLease]

  private val client = config.dynamoDBV2Builder().build()
  private val asyncClient = config.dynamoDBAsyncV2Builder().build()

  private val leasesTable = Table[MediaLease](config.leasesTable)

  def get(id: String)(implicit instance: Instance): Option[MediaLease] = {
    Scanamo(client).exec(leasesTable.get("id" === id and "instance" === instance.id)).flatMap(_.toOption)
  }

  def getForMedia(id: String)(implicit instance: Instance): List[MediaLease] = {
    Scanamo(client).exec(leasesTable.index("mediaId").query( "instance" === instance.id and "mediaId" === id)).flatMap(_.toOption)
  }

  def put(lease: MediaLease)(implicit instance: Instance) = {
    // TODO bypass scanomo to put on composite key
    val map: util.Map[String, AttributeValue] = formatLeases.write(lease).asObject.get.toJavaMap
    map.put("instance", AttributeValue.fromS(instance.id))
    val putRequest = PutItemRequest.builder().
      tableName(config.leasesTable)
      .item(map)
      .build()
    client.putItem(putRequest)
    Future.successful(true)
  }

  def putAll(leases: List[MediaLease])(implicit ec: ExecutionContext, instance: Instance) = {
    // TODO BATCH
    leases.foreach { l =>
      put(l)
    }
    Future.successful(true)
  }

  def delete(id: String)(implicit ec: ExecutionContext, instance: Instance) = {
    ScanamoAsync(asyncClient).exec(leasesTable.delete("id" === id and "instance" === instance.id))
  }

  def forEach(run: List[MediaLease] => Unit)(implicit ec: ExecutionContext, instance: Instance) = {
    val scanOperation = leasesTable.query("instance" === instance.id)
    val eventualOutcomes = ScanamoAsync(asyncClient).exec(scanOperation)
    eventualOutcomes.map(ops => ops.flatMap((t: Either[DynamoReadError, MediaLease]) => t.toOption))
      .map((a: Seq[MediaLease]) => run(a.toList)
      )
  }
}

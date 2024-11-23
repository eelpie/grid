package lib

import com.gu.mediaservice.model.Instance
import com.gu.mediaservice.model.leases.{MediaLease, MediaLeaseType}
import org.joda.time.DateTime
import org.scanamo._
import org.scanamo.generic.auto.{Typeclass, _}
import org.scanamo.syntax._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.concurrent.{ExecutionContext, Future}


class LeaseStore(tableName: String, client: DynamoDbAsyncClient) {

  implicit val dateTimeFormat: Typeclass[DateTime] =
    DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](DateTime.parse, _.toString)
  implicit val enumFormat: Typeclass[MediaLeaseType] =
    DynamoFormat.coercedXmap[MediaLeaseType, String, IllegalArgumentException](MediaLeaseType(_), _.toString)

  private val baseMediaLeaseFormat = DynamoFormat[MediaLease]

  // 2. Define a custom format that also writes the instance index field
  private def instanceAwareLeaseFormat(instance: Instance): DynamoFormat[MediaLease] = new DynamoFormat[MediaLease] {
    override def read(dv: DynamoValue): Either[DynamoReadError, MediaLease] = baseMediaLeaseFormat.read(dv)

    def write(l: MediaLease): DynamoValue = {
      val baseValue = baseMediaLeaseFormat.write(l)
      baseValue.asObject match {
        case Some(obj) =>
          val enrichedObj = obj + ("instance" -> DynamoValue.fromString(instance.id))
          enrichedObj.toDynamoValue
        case None =>
          // Fallback for safety, though case classes always derive to Maps
          baseValue
      }
    }
  }

  private val leasesTable = Table[MediaLease](tableName)

  def get(id: String)(implicit ec: ExecutionContext, instance: Instance): Future[Option[MediaLease]] = {
    ScanamoAsync(client).exec(leasesTable.get("id" === id and "instance" === instance.id)).map(_.flatMap(_.toOption))
  }

  def getForMedia(id: String)(implicit ec: ExecutionContext, instance: Instance): Future[List[MediaLease]] = {
    ScanamoAsync(client).exec(leasesTable.index("mediaId").query("mediaId" === id and "instance" === instance.id)).map(_.flatMap(_.toOption))
  }

  def put(lease: MediaLease)(implicit ec: ExecutionContext, instance: Instance) = {
    implicit val format: Typeclass[MediaLease] = instanceAwareLeaseFormat(instance)
    val t = Table[MediaLease](tableName)
    val op = t.put(lease)
    ScanamoAsync(client).exec(op)
  }

  def putAll(leases: List[MediaLease])(implicit ec: ExecutionContext, instance: Instance) = {
    implicit val format: Typeclass[MediaLease] = instanceAwareLeaseFormat(instance)
    val t = Table[MediaLease](tableName)
    ScanamoAsync(client).exec(t.putAll(leases.toSet))
  }

  def delete(id: String)(implicit ec: ExecutionContext, instance: Instance) = {
    ScanamoAsync(client).exec(leasesTable.delete("id" === id and "instance" === instance.id))
  }

  def forEach[T](run: List[MediaLease] => T)(implicit ec: ExecutionContext, instance: Instance) = {
    val instanceLeasesQuery = leasesTable.query("instance" === instance.id)
    ScanamoAsync(client).exec(instanceLeasesQuery).map(ops => ops.flatMap(_.toOption))
      .map(run)
  }
}

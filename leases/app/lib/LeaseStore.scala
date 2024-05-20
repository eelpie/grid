package lib

import com.gu.mediaservice.model.leases.{MediaLease, MediaLeaseType}
import org.joda.time.DateTime
import org.scanamo._
import org.scanamo.generic.semiauto.deriveDynamoFormat
import org.scanamo.syntax._
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbClient}

import scala.concurrent.ExecutionContext

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
      override def read(av: DynamoValue): Either[DynamoReadError, MediaLeaseType] = Right(MediaLeaseType(av.toAttributeValue.s()))
      override def write(t: MediaLeaseType): DynamoValue = DynamoValue.fromString(t.name)
    }
  }

  implicit val formatLeases: DynamoFormat[com.gu.mediaservice.model.leases.MediaLease] = deriveDynamoFormat[com.gu.mediaservice.model.leases.MediaLease]

  private val client = DynamoDbClient.builder.build() // TODO region and auth!
  private val asyncClient = DynamoDbAsyncClient.builder.build() // TODO region and auth!

  private val leasesTable = Table[MediaLease](config.leasesTable)

  def get(id: String): Option[MediaLease] = {
    Scanamo(client).exec(leasesTable.get("id" === id)).flatMap(_.toOption)
  }

  def getForMedia(id: String): List[MediaLease] = {
    Scanamo(client).exec(leasesTable.index("mediaId").query("mediaId" === id)).flatMap(_.toOption)
  }

  def put(lease: MediaLease)(implicit ec: ExecutionContext) = {
    ScanamoAsync(asyncClient).exec(leasesTable.put(lease))
  }

  def putAll(leases: List[MediaLease])(implicit ec: ExecutionContext) = {
    ScanamoAsync(asyncClient).exec(leasesTable.putAll(leases.toSet))
  }

  def delete(id: String)(implicit ec: ExecutionContext) = {
    ScanamoAsync(asyncClient).exec(leasesTable.delete("id" === id))
  }

  def forEach(run: List[MediaLease] => Unit)(implicit ec: ExecutionContext) = {
    val scanOperation = leasesTable.scan
    val eventualOutcomes = ScanamoAsync(asyncClient).exec(scanOperation)
    eventualOutcomes.map(ops => ops.flatMap((t: Either[DynamoReadError, MediaLease]) => t.toOption))
      .map((a: Seq[MediaLease]) => run(a.toList)
      )
  }
}

package lib

import com.gu.mediaservice.lib.BaseStore
import com.gu.mediaservice.lib.aws.S3
import com.gu.mediaservice.lib.logging.GridLogging
import com.gu.mediaservice.model.{Agencies, Agency, UsageRights}
import com.gu.mediaservice.model.usage.{DigitalUsage, PrintUsage, PublishedUsageStatus, RemovedUsageStatus, UnknownUsageStatus, Usage, UsageStatus, UsageType}
import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

case class SupplierUsageQuota(agency: Agency, count: Int)
object SupplierUsageQuota {
  implicit val writes: Writes[SupplierUsageQuota] = (
    (__ \ "agency").write[String].contramap((a: Agency) => a.supplier) ~
    (__ \ "count").write[Int]
  )(unlift(SupplierUsageQuota.unapply))

  implicit val customReads: Reads[SupplierUsageQuota] = (
    (__ \ "agency").read[String].map(Agency(_)) ~
    (__ \ "count").read[Int]
  )(SupplierUsageQuota.apply _)
}

case class SupplierQuotaCount(agency: Agency, count: Long)
object SupplierQuotaCount {
  implicit val customReads: Reads[SupplierQuotaCount] = (
    (__ \ "Supplier").read[String].map(Agency(_)) ~
    (__ \ "Usage").read[Long]
  )(SupplierQuotaCount.apply _)

  implicit val writes: Writes[SupplierQuotaCount] = (
    (__ \ "agency").write[String].contramap((a: Agency) => a.supplier) ~
    (__ \ "count").write[Long]
  )(unlift(SupplierQuotaCount.unapply))
}

case class SupplierUsageStatus(
  exceeded: Boolean,
  fractionOfQuota: Float,
  usage: SupplierQuotaCount,
  quota: Option[SupplierUsageQuota]
)
object SupplierUsageStatus {
  implicit val writes: Writes[SupplierUsageStatus] = Json.writes[SupplierUsageStatus]
}

case class StoreAccess(store: Map[String, SupplierUsageStatus], lastUpdated: DateTime)
object StoreAccess {
  import play.api.libs.json.JodaWrites._

  implicit val writes: Writes[StoreAccess] = Json.writes[StoreAccess]
}

object UsageStore extends GridLogging {
  val countQualifyingStatuses: Set[UsageStatus] = Set(PublishedUsageStatus, UnknownUsageStatus, RemovedUsageStatus)
  val countQualifyingPlatforms: Set[UsageType] = Set(PrintUsage, DigitalUsage)
  val countPeriodInDays: Int = 30
}

class UsageStore(
  bucket: String,
  config: MediaApiConfig,
  quotaStore: QuotaStore,
  s3: S3
)(implicit val ec: ExecutionContext) extends BaseStore[String, SupplierUsageStatus](bucket, config, s3) with GridLogging {

  def getUsageStatusForUsageRights(usageRights: UsageRights): Future[SupplierUsageStatus] = {
    usageRights match {
      case agency: Agency => Future.successful(store.get().getOrElse(agency.supplier, { throw NoUsageQuota() }))
      case _ => Future.failed(new Exception("Image is not supplied by Agency"))
    }
  }

  def getUsageStatus: Future[StoreAccess] = {
    val quota = quotaStore.getQuota

    val results = quota.keys.flatMap { supplier =>
      val maybeAgency = Agencies.all.get(supplier)
      maybeAgency.map { agency =>
        val supplierUsageSummary: SupplierQuotaCount = SupplierQuotaCount(
          agency = agency, count = 0
        )
        val supplierUsageQuota: SupplierUsageQuota = SupplierUsageQuota(
          agency = agency, count = quota.get(supplier).map(_.count).getOrElse(0)
        )
        val supplierUsageStatus = SupplierUsageStatus(
          exceeded = false,
          fractionOfQuota = 0.0.toFloat,
          usage = supplierUsageSummary,
          quota = Some(supplierUsageQuota)
        )
        (supplier, supplierUsageStatus)
      }
    }.toMap

    Future.successful(StoreAccess(store = results, lastUpdated = DateTime.now()))
  }

  def overQuotaAgencies: List[Agency] = store.get.collect {
    case (_, status) if status.exceeded => status.usage.agency
  }.toList

  def update(): Unit = {
      // TODO reimplement
  }

}



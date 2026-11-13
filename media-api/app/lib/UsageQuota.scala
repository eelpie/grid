package lib

import org.apache.pekko.actor.Scheduler
import com.gu.mediaservice.lib.FeatureToggle
import com.gu.mediaservice.lib.aws.S3
import com.gu.mediaservice.lib.instances.InstancesClient
import com.gu.mediaservice.model.{Instance, UsageRights}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

case class ImageNotFound() extends Exception("Image not found")
case class NoUsageQuota() extends Exception("No usage found for this image")

class UsageQuota(config: MediaApiConfig, scheduler: Scheduler, getSupplierQuotaCount: (String, Instance) => Future[SupplierQuotaCount], s3: S3, instancesClient: InstancesClient) {
  val quotaStore = new QuotaStore(
    config.quotaStoreConfig.storeKey,
    config.quotaStoreConfig.storeBucket,
    config,
    s3,
    instancesClient
  )

  val usageStore = new UsageStore(
    getSupplierQuotaCount,
    quotaStore,
    instancesClient
  )

  def scheduleUpdates(): Unit = {
    quotaStore.scheduleUpdates(scheduler)
    usageStore.scheduleUpdates(scheduler)
  }

  def stopUpdates(): Unit = {
    quotaStore.stopUpdates()
    usageStore.stopUpdates()
  }

  def isOverQuota(rights: UsageRights, waitMillis: Int = 100): Boolean = Try {
    Await.result(
      usageStore.getUsageStatusForUsageRights(rights),
      waitMillis.millis)
  }.toOption.exists(_.exceeded) && FeatureToggle.get("usage-quota-ui")
}


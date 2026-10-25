package lib

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.metrics.{LongCounter, LongGauge}
import io.opentelemetry.sdk.OpenTelemetrySdk
import org.apache.pekko.actor.ActorSystem
import com.gu.mediaservice.lib.metrics.CloudWatchMetrics
import com.gu.mediaservice.model.Instance
import play.api.inject.ApplicationLifecycle
import software.amazon.awssdk.services.cloudwatch.model.Dimension

import scala.concurrent.ExecutionContext

class ThrallMetrics(config: ThrallConfig, actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext)
  extends CloudWatchMetrics(s"${config.stage}/Thrall", config, actorSystem, applicationLifecycle) {

  private val meter = ThrallMetrics.openTelemetrySdk.getMeter("thrall")

  // Count metrics which are dual written to CloudWatch and to an OpenTelemetry counter.
  private class DualWrittenCountMetric(name: String, openTelemetryName: String) extends CountMetric(name) {
    private val openTelemetryCounter: LongCounter = meter.counterBuilder(openTelemetryName).build()

    override def increment(dimensions: List[Dimension] = Nil, n: Long = 1): Unit = {
      super.increment(dimensions, n)
      openTelemetryCounter.add(n)
    }

    override def incrementBothWithAndWithoutDimensions(dimensions: List[Dimension], n: Long = 1): Unit = {
      super.incrementBothWithAndWithoutDimensions(dimensions, n)
      openTelemetryCounter.add(n)
    }
  }

  // Point in time totals; recorded as a CloudWatch count (as before) and an OpenTelemetry gauge per instance.
  class DualWrittenGaugeMetric(name: String, openTelemetryName: String) extends CountMetric(name) {
    private val openTelemetryGauge: LongGauge = meter.gaugeBuilder(openTelemetryName).ofLongs().build()

    def record(n: Long, instance: Instance): Unit = {
      super.increment(Nil, n)
      openTelemetryGauge.set(n, Attributes.of(AttributeKey.stringKey("instance"), instance.id))
    }
  }

  val indexedImages = new CountMetric("IndexedImages")

  val deletedImages: CountMetric = new DualWrittenCountMetric("DeletedImages", "images.deleted")

  val softReaped: CountMetric = new DualWrittenCountMetric("SoftReaped", "reaper.soft.reaped")
  val hardReaped: CountMetric = new DualWrittenCountMetric("HardReaped", "reaper.hard.reaped")
  val softReapable = new DualWrittenGaugeMetric("SoftReapable", "reaper.soft.reapable")
  val hardReapable = new DualWrittenGaugeMetric("HardReapable", "reaper.hard.reapable")

  val failedDeletedImages: CountMetric = new DualWrittenCountMetric("FailedDeletedImages", "images.deleted.failed")

  val failedMetadataUpdates = new CountMetric("FailedMetadataUpdates")

  val failedCollectionsUpdates = new CountMetric("FailedCollectionsUpdates")

  val failedExportsUpdates = new CountMetric("FailedExportsUpdates")

  val failedEmbeddingUpdates = new CountMetric("FailedEmbeddingUpdates")

  val failedUsagesUpdates = new CountMetric("FailedUsagesUpdates")

  val failedSyndicationRightsUpdates = new CountMetric("FailedSyndicationRightsUpdates")

  val failedQueryUpdates = new CountMetric("FailedQueryUpdates")

  val failedDeletedAllUsages = new CountMetric("FailedDeletedAllUsages")

  val processingLatency = new TimeMetric("ProcessingLatency")

  val snsMessage = new CountMetric("SNSMessage")

}

object ThrallMetrics {
  import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

  private val openTelemetrySdk: OpenTelemetrySdk = AutoConfiguredOpenTelemetrySdk.builder()
    .build()
    .getOpenTelemetrySdk
}

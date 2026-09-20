package lib

import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.sdk.OpenTelemetrySdk
import org.apache.pekko.actor.ActorSystem
import com.gu.mediaservice.lib.metrics.CloudWatchMetrics
import play.api.inject.ApplicationLifecycle
import software.amazon.awssdk.services.cloudwatch.model.Dimension

class ImageLoaderMetrics(config: ImageLoaderConfig, actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle)
    extends CloudWatchMetrics (namespace = s"${config.stage}/ImageLoader", config, actorSystem, applicationLifecycle){

  private val meter = ImageLoaderMetrics.openTelemetrySdk.getMeter("image-loader")

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

  val successfulIngestsFromQueue: CountMetric = new DualWrittenCountMetric("SuccessfulIngestsFromQueue", "ingest.queue.successful")

  val failedIngestsFromQueue: CountMetric = new DualWrittenCountMetric("FailedIngestsFromQueue", "ingest.queue.failed")

  val abandonedMessagesFromQueue: CountMetric = new DualWrittenCountMetric("AbandonedMessagesFromQueue", "ingest.queue.abandoned")
}

object ImageLoaderMetrics {
  import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
  private val openTelemetrySdk: OpenTelemetrySdk = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk
}

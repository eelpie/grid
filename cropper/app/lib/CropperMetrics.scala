package lib

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.sdk.OpenTelemetrySdk

class CropperMetrics {

  private val meter = CropperMetrics.openTelemetrySdk.getMeter("cropper")

  private val cropGenerationDurationHistogram: DoubleHistogram = meter
    .histogramBuilder(CropperMetrics.cropGenerationDurationName)
    .setDescription("Time taken to generate crop assets on local disk")
    .setUnit("s")
    .build()

  def recordCropGenerationDuration(durationNanos: Long, succeeded: Boolean): Unit = {
    val attributes = Attributes.of(AttributeKey.booleanKey("succeeded"), java.lang.Boolean.valueOf(succeeded))
    cropGenerationDurationHistogram.record(durationNanos / 1e9, attributes)
  }
}

object CropperMetrics {
  import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

  val cropGenerationDurationName = "crop.generation.duration"

  private val openTelemetrySdk: OpenTelemetrySdk = AutoConfiguredOpenTelemetrySdk.builder()
    .build()
    .getOpenTelemetrySdk
}

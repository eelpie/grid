package com.gu.mediaservice.lib.metrics

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter

object OpenTelemetryMetrics {

  private val meter = GlobalOpenTelemetry.meterBuilder("grid")
    .setInstrumentationVersion("1.0.0")
    .build()

  class CountMetric(name: String) {
    private val counter: LongCounter = meter.counterBuilder(name)
      .setUnit("1")
      .build()

    def increment(n: Long = 1): Unit = {
      counter.add(n)  // TODO attributes
    }
  }
}

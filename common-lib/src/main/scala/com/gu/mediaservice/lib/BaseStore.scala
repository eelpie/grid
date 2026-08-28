package com.gu.mediaservice.lib

import com.gu.mediaservice.lib.aws.S3
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.logging.GridLogging
import org.apache.pekko.actor.{Cancellable, Scheduler}
import org.joda.time.DateTime

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal


abstract class BaseStore[TStoreKey, TStoreVal](bucket: String, config: CommonConfig, s3: S3)(implicit ec: ExecutionContext)
  extends GridLogging {

  protected val store: AtomicReference[Map[TStoreKey, TStoreVal]] = new AtomicReference(Map.empty)
  protected val lastUpdated: AtomicReference[DateTime] = new AtomicReference(DateTime.now())

  protected def getS3Object(key: String): Option[String] = s3.getObjectAsString(bucket, key)

  private var cancellable: Option[Cancellable] = None

  def scheduleUpdates(scheduler: Scheduler): Unit = {
    cancellable = Some(scheduler.scheduleAtFixedRate(0.seconds, 10.minutes)(() => {
      try {
        update()
        lastUpdated.set(DateTime.now())
      } catch {
        case NonFatal(e) => logger.error("Store update failed", e)
      }
    }))
  }

  def stopUpdates(): Unit = {
    cancellable.foreach(_.cancel())
  }

  def update(): Unit
}

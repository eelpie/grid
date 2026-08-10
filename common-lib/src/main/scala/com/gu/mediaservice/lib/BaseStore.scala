package com.gu.mediaservice.lib

import com.gu.mediaservice.lib.aws.{S3, S3Bucket}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.logging.GridLogging
import org.apache.pekko.actor.{Cancellable, Scheduler}
import org.joda.time.DateTime

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal


abstract class BaseStore[TStoreKey, TStoreVal](bucket: S3Bucket, config: CommonConfig)(implicit ec: ExecutionContext)
  extends GridLogging {

  val s3 = new S3(config)

  protected val store: AtomicReference[Map[TStoreKey, TStoreVal]] = new AtomicReference(Map.empty)
  protected val lastUpdated: AtomicReference[DateTime] = new AtomicReference(DateTime.now())

  protected def getS3Object(key: String): Option[String] = s3.getObjectAsStringV2(bucket, key)

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

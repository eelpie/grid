package com.gu.mediaservice.lib.auth

import com.gu.mediaservice.lib.BaseStore
import com.gu.mediaservice.lib.aws.{S3, S3Bucket}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.model.Instance

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class KeyStore(bucket: S3Bucket, config: CommonConfig, s3: S3)(implicit ec: ExecutionContext)
  extends BaseStore[String, ApiAccessor](bucket, config, s3)(ec) {

  def lookupIdentity(key: String)(implicit instance: Instance): Option[ApiAccessor] = store.get().get(instance.id + "/" + key)

  def findKey(prefix: String)(implicit instance: Instance): Option[String] = s3.syncFindKey(bucket, prefix)

  def update(): Unit = {
    store.set(fetchAll)
  }

  private def fetchAll: Map[String, ApiAccessor] = {
    val objects = Await.result(s3.list(bucket, ""), 10.seconds)
    val keys = objects.map( s3Object => bucket.keyFromURL(s3Object.uri))
    keys.flatMap(k => getS3Object(k).map(k -> ApiAccessor(_))).toMap
  }
}

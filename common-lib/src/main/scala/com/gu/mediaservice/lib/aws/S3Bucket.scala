package com.gu.mediaservice.lib.aws

import software.amazon.awssdk.services.s3.S3Client

import java.net.URI

case class S3Bucket(bucket: String, endPoint: String, usesPathStyleURLs: Boolean, client: S3Client) {

  def objectUrl(key: String): URI = {
    val bucketUrl = s"$bucket.$endPoint"
    new URI("http", bucketUrl, s"/$key", null)
  }

}

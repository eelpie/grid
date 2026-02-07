package com.gu.mediaservice.lib.aws

import com.amazonaws.services.s3.AmazonS3

import java.net.URI

case class S3Bucket(bucket: String, endpoint: String, usesPathStyleURLs: Boolean, client: AmazonS3) {
  def objectUrl(key: String): URI = {
    val bucketBaseURL = bucketURL()
    new URI("http", bucketBaseURL.getHost, bucketBaseURL.getPath + key, null)
  }

  def keyFromS3URL(url: URI): String = {
    if (usesPathStyleURLs) {
      url.getPath.drop(bucket.length + 2)
    } else {
      url.getPath.drop(1)
    }
  }

  def bucketURL(): URI = {
    if (usesPathStyleURLs) {
      new URI("https", endpoint, s"/$bucket/", null)
    } else {
      new URI("https", s"$bucket.$endpoint", "/", null)
    }
  }

}

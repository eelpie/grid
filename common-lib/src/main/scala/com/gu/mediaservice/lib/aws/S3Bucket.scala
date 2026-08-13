package com.gu.mediaservice.lib.aws

import software.amazon.awssdk.services.s3.S3Client

import java.net.URI

case class S3Bucket(bucket: String, endPoint: String, usesPathStyleURLs: Boolean, client: S3Client) {

  def objectUrl(key: String): URI = {
    val bucketBaseURL = bucketURL()
    new URI("http", bucketBaseURL.getHost, bucketBaseURL.getPath + key, null)
  }

  def keyFromURL(url: URI): String = {
    if (usesPathStyleURLs) {
      url.getPath.drop(bucket.length + 2)
    } else {
      // get path and remove leading `/`
      url.getPath.drop(1)
    }
  }

  private def bucketURL(): URI = {
    if (usesPathStyleURLs) {
      new URI("https", endPoint, s"/$bucket/", null)
    } else {
      new URI("https", s"$bucket.$endPoint", "/", null)
    }
  }

}

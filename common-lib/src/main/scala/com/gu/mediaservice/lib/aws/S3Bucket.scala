package com.gu.mediaservice.lib.aws

import com.gu.mediaservice.lib.config.CommonConfig
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

case class S3Bucket(name: String, endPoint: String, usesPathStyleURLs: Boolean, client: S3Client, presigner: S3Presigner)

object S3Bucket {

  /**
    * Build a bucket that talks to the endpoint implied by the current environment - i.e. the localstack
    * endpoint (with path style URLs) when running in DEV, otherwise the real AWS S3 endpoint.
    */
  def apply(name: String, config: CommonConfig): S3Bucket = {
    val endpointOverride = config.awsLocalEndpoint
    val usesPathStyleURLs = endpointOverride.isDefined
    apply(name, config, endpointOverride, usesPathStyleURLs)
  }

  def apply(name: String, config: CommonConfig, endpointOverride: Option[String], usesPathStyleURLs: Boolean): S3Bucket =
    S3Bucket(
      name = name,
      endPoint = endpointOverride.getOrElse(S3Ops.s3Endpoint),
      usesPathStyleURLs = usesPathStyleURLs,
      client = S3Ops.buildS3Client(config, endpointOverride, usesPathStyleURLs),
      presigner = S3Ops.buildPresignerClientV2(config, endpointOverride, usesPathStyleURLs)
    )
}

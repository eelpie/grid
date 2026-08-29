package com.gu.mediaservice.lib.aws

import com.gu.mediaservice.lib.config.CommonConfig
import com.typesafe.config.ConfigException
import play.api.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

case class S3Bucket(name: String, endPoint: URI, usesPathStyleURLs: Boolean, client: S3Client, presigner: S3Presigner) {

  def objectUrl(key: String): URI = {
    val bucketBaseURL = bucketURL()
    new URI(bucketBaseURL.getScheme, bucketBaseURL.getHost, bucketBaseURL.getPath + key, null)
  }

  def keyFromURL(url: URI): String = {
    if (usesPathStyleURLs) {
      url.getPath.drop(name.length + 2)
    } else {
      // get path and remove leading `/`
      url.getPath.drop(1)
    }
  }

  def bucketURL(): URI = {
    if (usesPathStyleURLs) {
      new URI(endPoint.getScheme, endPoint.getHost, s"/$name/", null)
    } else {
      new URI(endPoint.getScheme, s"$name.${endPoint.getHost}", "/", null)
    }
  }

}

object S3Bucket {

  /**
   * Build a bucket that talks to the endpoint implied by the current environment - i.e. the localstack
   * endpoint (with path style URLs) when running in DEV, otherwise the real AWS S3 endpoint.
   */
  def apply(name: String, config: CommonConfig): S3Bucket = {
    val endpointOverride = config.awsLocalEndpoint
    val usesPathStyleURLs = endpointOverride.isDefined
    apply(name, config, endpointOverride, usesPathStyleURLs, None)
  }

  def apply(name: String, config: CommonConfig, endpointOverride: Option[String], usesPathStyleURLs: Boolean, maybeRegionOverride: Option[Region]): S3Bucket =
    S3Bucket(
      name = name,
      endPoint = new URI(endpointOverride.getOrElse(S3Ops.s3Endpoint)),
      usesPathStyleURLs = usesPathStyleURLs,
      client = S3Ops.buildS3Client(config, endpointOverride, usesPathStyleURLs, maybeRegionOverride),
      presigner = S3Ops.buildPresignerClientV2(config, endpointOverride, usesPathStyleURLs, maybeRegionOverride)
    )

  /**
   * Build a bucket from a config block of the form:
   * {{{
   *   s3.image.bucket {
   *     name          = "media-service-image-bucket"  # required
   *     endpoint      = "s3.eu-west-1.amazonaws.com"   # optional
   *     pathStyleUrls = false                          # optional, defaults to `endpoint` being set
   *     region        = "eu-west-1"                    # optional
   *   }
   * }}}
   * where `bucketConfigPath` is the path to the block, e.g. "s3.image.bucket".
   *
   * When `endpoint` is absent the bucket falls back to the localstack endpoint in DEV and the
   * real AWS S3 endpoint otherwise (the same behaviour as `S3Bucket(name, config)`).
   */
  def fromConfig(bucketConfigPath: String, config: CommonConfig): S3Bucket =
    fromConfig(config.configuration, bucketConfigPath, config)

  def fromConfigOpt(bucketConfigPath: String, config: CommonConfig): Option[S3Bucket] =
    fromConfigOpt(config.configuration, bucketConfigPath, config)

  /** As `fromConfig` but reading the block from an arbitrary (possibly scoped) `Configuration`
    * - e.g. an auth provider's own config block. */
  def fromConfig(source: Configuration, bucketConfigPath: String, config: CommonConfig): S3Bucket =
    fromConfigOpt(source, bucketConfigPath, config).getOrElse(
      throw new IllegalStateException(s"Missing required S3 bucket config: '$bucketConfigPath.name'")
    )

  def fromConfigOpt(source: Configuration, bucketConfigPath: String, config: CommonConfig): Option[S3Bucket] = {
    val maybeName = try {
      source.getOptional[String](s"$bucketConfigPath.name")
    } catch {
      case e: ConfigException.WrongType =>
        throw new IllegalStateException(
          s"S3 bucket config '$bucketConfigPath' must be an object with a 'name' key, " +
            s"""e.g. `$bucketConfigPath { name = "my-bucket" }`. """ +
            s"""The legacy string form `$bucketConfigPath = "my-bucket"` is no longer supported.""",
          e
        )
    }
    maybeName.map { name =>
      val endpointOverride = source.getOptional[String](s"$bucketConfigPath.endpoint").filter(_.nonEmpty)
        .orElse(config.awsLocalEndpoint)
      val usesPathStyleURLs = source.getOptional[Boolean](s"$bucketConfigPath.pathStyleUrls")
        .getOrElse(endpointOverride.isDefined)
      val maybeRegionOverride = source.getOptional[String](s"$bucketConfigPath.region").filter(_.nonEmpty).map(Region.of)
      apply(name, config, endpointOverride, usesPathStyleURLs, maybeRegionOverride)
    }
  }
}

package lib

import java.net.URI

import com.amazonaws.regions.{Region, RegionUtils}
import com.amazonaws.services.identitymanagement._
import com.gu.mediaservice.lib.config.{CommonConfig, Properties}
import com.gu.mediaservice.lib.net.URI.ensureSecure
import play.api.{Configuration, Logger}

import scala.util.Try

case class KinesisReaderConfig(streamName: String, arn: String, appName: String)

class UsageConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "usage"

  val defaultPageSize = 100
  val defaultMaxRetries = 6
  val defaultMaxPrintRequestSizeInKb = 500
  val defaultDateLimit = "2016-01-01T00:00:00+00:00"

  // TODO want to drive out this Guardian native config file
  lazy val properties: Map[String, String] = Properties.fromPath(s"/etc/gu/$appName.properties")

  val maxPrintRequestLengthInKb: Int = Try(properties("api.setPrint.maxLength").toInt).getOrElse[Int](defaultMaxPrintRequestSizeInKb)

  val capiLiveUrl: String = properties("capi.live.url")
  val capiApiKey: String = properties("capi.apiKey")
  val capiPageSize: Int = Try(properties("capi.page.size").toInt).getOrElse[Int](defaultPageSize)
  val capiMaxRetries: Int = Try(properties("capi.maxRetries").toInt).getOrElse[Int](defaultMaxRetries)

  val usageDateLimit: String = Try(properties("usage.dateLimit")).getOrElse(defaultDateLimit)

  val topicArn: Option[String] = configuration.getOptional[String]("sns.topic.arn")

  private val composerBaseUrlProperty: String = properties("composer.baseUrl")
  private val composerBaseUrl: URI = ensureSecure(composerBaseUrlProperty)

  val composerContentBaseUrl: String = s"$composerBaseUrl/content"

  val usageRecordTable: String = properties("dynamo.tablename.usageRecordTable")

  val dynamoRegion: Region = RegionUtils.getRegion(properties("aws.region"))
  val awsRegionName: String = properties("aws.region")

  val crierLiveKinesisStream: Try[String] = Try {
    properties("crier.live.name")
  }
  val crierPreviewKinesisStream: Try[String] = Try {
    properties("crier.preview.name")
  }

  val crierLiveArn: Try[String] = Try {
    properties("crier.live.arn")
  }
  val crierPreviewArn: Try[String] = Try {
    properties("crier.preview.arn")
  }

  lazy val liveKinesisReaderConfig: Try[KinesisReaderConfig] = for {
    liveStream <- crierLiveKinesisStream
    liveArn <- crierLiveArn
  } yield KinesisReaderConfig(liveStream, liveArn, liveAppName)

  lazy val previewKinesisReaderConfig: Try[KinesisReaderConfig] = for {
    previewStream <- crierPreviewKinesisStream
    previewArn <- crierPreviewArn
  } yield KinesisReaderConfig(previewStream, previewArn, previewAppName)

  private val iamClient: AmazonIdentityManagement = withAWSCredentials(AmazonIdentityManagementClientBuilder.standard()).build()

  val postfix: String = if (isDev) {
    try {
      iamClient.getUser.getUser.getUserName
    } catch {
      case e: com.amazonaws.AmazonServiceException =>
        Logger.warn("Unable to determine current IAM user, probably because you're using temp credentials.  Usage may not be able to determine the live/preview app names")
        "tempcredentials"
    }
  } else {
    stage
  }

  val liveAppName = s"media-service-livex-$postfix"
  val previewAppName = s"media-service-previewx-$postfix"

  val apiOnly: Boolean = Try(properties("app.name")).toOption match {
    case Some("usage-stream") =>
      Logger.info(s"Starting as Stream Reader Usage.")
      false
    case Some("usage") =>
      Logger.info(s"Starting as API only Usage.")
      true
    case name =>
      Logger.error(s"App name is invalid: $name")
      sys.exit(1)
  }

}

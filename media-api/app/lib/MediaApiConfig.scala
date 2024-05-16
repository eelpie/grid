package lib

import com.amazonaws.services.cloudfront.util.SignerUtils
import com.gu.mediaservice.lib.config.{CommonConfigWithElastic, GridConfigResources}
import org.joda.time.DateTime
import play.api.mvc.RequestHeader

import java.security.PrivateKey
import scala.util.Try

case class StoreConfig(
  storeBucket: String,
  storeKey: String
)

class MediaApiConfig(resources: GridConfigResources) extends CommonConfigWithElastic(resources) {
  val configBucket: String = string("s3.config.bucket")
  val usageMailBucket: String = string("s3.usagemail.bucket")

  val quotaStoreKey: String = string("quota.store.key")
  val quotaStoreConfig: StoreConfig = StoreConfig(configBucket, quotaStoreKey)

  //Lazy allows this to be empty and not break things unless used somewhere
  lazy val imgPublishingBucket = string("publishing.image.bucket")

  val imageBucket: String = string("s3.image.bucket")
  val thumbBucket: String = string("s3.thumb.bucket")

  val cloudFrontDomainThumbBucket: Option[String]   = stringOpt("cloudfront.domain.thumbbucket")
  val cloudFrontPrivateKeyBucket: Option[String]    = stringOpt("cloudfront.private-key.bucket")
  val cloudFrontPrivateKeyBucketKey: Option[String] = stringOpt("cloudfront.private-key.key")
  val cloudFrontKeyPairId: Option[String]           = stringOpt("cloudfront.keypair.id")

  val rootUri: RequestHeader => String = services.apiBaseUri
  val kahunaUri: RequestHeader => String = services.kahunaBaseUri
  val cropperUri: RequestHeader => String = services.cropperBaseUri
  val loaderUri: RequestHeader => String = services.loaderBaseUri
  val metadataUri: RequestHeader => String = services.metadataBaseUri
  val imgopsUri: RequestHeader => String = services.imgopsBaseUri
  val usageUri: RequestHeader => String = services.usageBaseUri
  val leasesUri: RequestHeader => String = services.leasesBaseUri
  val authUri: RequestHeader => String = services.authBaseUri
  val collectionsUri: RequestHeader => String = services.collectionsBaseUri

  val requiredMetadata = List("credit", "description", "usageRights")

  val syndicationStartDate: Option[DateTime] = Try {
    stringOpt("syndication.start").map(d => DateTime.parse(d).withTimeAtStartOfDay())
  }.toOption.flatten
  val useRuntimeFieldsToFixSyndicationReviewQueueQuery = boolean("syndication.review.useRuntimeFieldsFix")

  //BBC custom validity description messages
  val customValidityDescription: Map[String, String] =
    configuration.getOptional[Map[String, String]]("warningText.validityDescription").getOrElse(Map.empty)

  val customSpecialInstructions: Map[String, String] =
    configuration.getOptional[Map[String, String]]("usageInstructions").getOrElse(Map.empty)

  val customUsageRestrictions: Map[String, String] =
    configuration.getOptional[Map[String, String]]("usageRestrictions").getOrElse(Map.empty)

  val restrictDownload: Boolean = boolean("restrictDownload")

}

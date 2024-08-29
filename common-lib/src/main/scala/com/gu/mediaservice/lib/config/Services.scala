package com.gu.mediaservice.lib.config

import com.gu.mediaservice.model.Instance

trait Services {

  def kahunaBaseUri: String

  def apiBaseUri(instance: Instance): String

  def loaderBaseUri: String

  def projectionBaseUri: String

  def cropperBaseUri(instance: Instance): String

  def metadataBaseUri(instance: Instance): String

  def imgopsBaseUri(instance: Instance): String

  def usageBaseUri: String

  def collectionsBaseUri: String

  def leasesBaseUri: String

  def authBaseUri: String

  def guardianWitnessBaseUri: String

  def corsAllowedDomains(instance: Instance): Set[String]

  def redirectUriParam: String

  def redirectUriPlaceholder: String

  def loginUriTemplate: String

}

protected class SingleHostServices(val rootUrl: String) extends Services {
  val kahunaBaseUri: String = rootUrl

  override def apiBaseUri(instance: Instance): String = vhostServiceName("media-api", instance)

  val loaderBaseUri: String = subpathedServiceBaseUri("image-loader")

  val projectionBaseUri: String = loaderBaseUri

  override def cropperBaseUri(instance: Instance): String = vhostServiceName("cropper", instance)

  override def metadataBaseUri(instance: Instance): String = vhostServiceName("metadata-editor", instance)

  override def imgopsBaseUri(instance: Instance): String=  vhostServiceName("imgproxy", instance)

  val usageBaseUri: String =subpathedServiceBaseUri("usage")

  val collectionsBaseUri: String = subpathedServiceBaseUri("collections")

  val leasesBaseUri: String = subpathedServiceBaseUri("leases")

  val authBaseUri: String = subpathedServiceBaseUri("auth")

  private val thrallBaseUri: String =  subpathedServiceBaseUri("thrall")

  val guardianWitnessBaseUri: String = "https://n0ticeapis.com"

  override def corsAllowedDomains(instance: Instance): Set[String] = Set(kahunaBaseUri, apiBaseUri(instance), thrallBaseUri)

  val redirectUriParam = "redirectUri"
  val redirectUriPlaceholder = s"{?$redirectUriParam}"
  val loginUriTemplate = s"$authBaseUri/login$redirectUriPlaceholder"

  private def vhostServiceName(serviceName: String, instance: Instance): String = {
    val vhostRootUrl = instance.id
    s"https://$vhostRootUrl/" + serviceName
  }

  private def subpathedServiceBaseUri(serviceName: String): String = s"$rootUrl/$serviceName"
}


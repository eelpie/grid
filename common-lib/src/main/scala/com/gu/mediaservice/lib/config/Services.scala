package com.gu.mediaservice.lib.config

import com.gu.mediaservice.model.Instance
import play.api.mvc.RequestHeader

trait Services {

  def kahunaBaseUri(instance: Instance): String

  def apiBaseUri(instance: Instance): String

  def loaderBaseUri(instance: Instance): String

  def projectionBaseUri(instance: Instance): String

  def cropperBaseUri(instance: Instance): String

  def metadataBaseUri(instance: Instance): String

  def imgopsBaseUri(instance: Instance): String

  def usageBaseUri(instance: Instance): String

  def collectionsBaseUri(instance: Instance): String

  def leasesBaseUri(instance: Instance): String

  def authBaseUri(instance: Instance): String

  def guardianWitnessBaseUri: String

  def corsAllowedDomains(instance: Instance): Set[String]

  def redirectUriParam: String

  def redirectUriPlaceholder: String

  def loginUriTemplate(instance: Instance): String
}

case class ServiceHosts(
                         kahunaPrefix: String,
                         apiPrefix: String,
                         loaderPrefix: String,
                         projectionPrefix: String,
                         cropperPrefix: String,
                         metadataPrefix: String,
                         imgopsPrefix: String,
                         usagePrefix: String,
                         collectionsPrefix: String,
                         leasesPrefix: String,
                         authPrefix: String,
                         thrallPrefix: String
                       )

object ServiceHosts {
  // this is tightly coupled to the Guardian's deployment.
  // TODO make more generic but w/out relying on Play config
  def guardianPrefixes: ServiceHosts = {
    val rootAppName: String = "media"

    ServiceHosts(
      kahunaPrefix = s"$rootAppName.",
      apiPrefix = s"api.$rootAppName.",
      loaderPrefix = s"loader.$rootAppName.",
      projectionPrefix = s"loader-projection.$rootAppName",
      cropperPrefix = s"cropper.$rootAppName.",
      metadataPrefix = s"$rootAppName-metadata.",
      imgopsPrefix = s"$rootAppName-imgops.",
      usagePrefix = s"$rootAppName-usage.",
      collectionsPrefix = s"$rootAppName-collections.",
      leasesPrefix = s"$rootAppName-leases.",
      authPrefix = s"$rootAppName-auth.",
      thrallPrefix = s"thrall.$rootAppName."
    )
  }
}

protected class SingleHostServices(val rootUrl: String) extends Services {
  override def kahunaBaseUri(instance: Instance): String =  vhostServiceName("", instance)

  override def apiBaseUri(instance: Instance): String=  vhostServiceName("media-api", instance)

  override def loaderBaseUri(instance: Instance): String = vhostServiceName("image-loader", instance)

  override def projectionBaseUri(instance: Instance): String = vhostServiceName("projection", instance)

  override def cropperBaseUri(instance: Instance): String = vhostServiceName("cropper", instance)

  override def metadataBaseUri(instance: Instance): String = vhostServiceName("metadata-editor", instance)

  override def imgopsBaseUri(instance: Instance): String = vhostServiceName("imgops", instance)

  override def usageBaseUri(instance: Instance): String = vhostServiceName("usage", instance)

  override def collectionsBaseUri(instance: Instance): String = vhostServiceName("collections", instance)

  override def leasesBaseUri(instance: Instance): String = vhostServiceName("leases", instance)

  override def authBaseUri(instance: Instance): String = vhostServiceName("auth", instance)

  private def thrallBaseUri(instance: Instance): String = vhostServiceName("thrall", instance)

  val guardianWitnessBaseUri: String = "https://n0ticeapis.com"

  override def corsAllowedDomains(instance: Instance): Set[String] = Set(kahunaBaseUri(instance), apiBaseUri(instance), thrallBaseUri(instance))

  val redirectUriParam = "redirectUri"
  val redirectUriPlaceholder = s"{?$redirectUriParam}"
  def loginUriTemplate(instance: Instance): String = s"${authBaseUri(instance)}/login$redirectUriPlaceholder"

  private def vhostServiceName(serviceName: String, instance: Instance): String = {
    val vhostRootUrl = instance.id
    s"https://$vhostRootUrl/" + serviceName
  }
}

protected class GuardianUrlSchemeServices(domainRoot: String, hosts: ServiceHosts, corsAllowedOrigins: Set[String], domainRootOverride: Option[String] = None) extends Services {
  private val kahunaHost: String = s"${hosts.kahunaPrefix}$domainRoot"
  private val apiHost: String = s"${hosts.apiPrefix}$domainRoot"
  private val loaderHost: String = s"${hosts.loaderPrefix}${domainRootOverride.getOrElse(domainRoot)}"
  private val cropperHost: String = s"${hosts.cropperPrefix}${domainRootOverride.getOrElse(domainRoot)}"
  private val metadataHost: String = s"${hosts.metadataPrefix}${domainRootOverride.getOrElse(domainRoot)}"
  private val imgopsHost: String = s"${hosts.imgopsPrefix}${domainRootOverride.getOrElse(domainRoot)}"
  private val usageHost: String = s"${hosts.usagePrefix}${domainRootOverride.getOrElse(domainRoot)}"
  private val collectionsHost: String = s"${hosts.collectionsPrefix}${domainRootOverride.getOrElse(domainRoot)}"
  private val leasesHost: String = s"${hosts.leasesPrefix}${domainRootOverride.getOrElse(domainRoot)}"
  private val authHost: String = s"${hosts.authPrefix}$domainRoot"
  private val projectionHost: String = s"${hosts.projectionPrefix}${domainRootOverride.getOrElse(domainRoot)}"
  private val thrallHost: String = s"${hosts.thrallPrefix}${domainRootOverride.getOrElse(domainRoot)}"

  override def kahunaBaseUri(instance: Instance) = baseUri(kahunaHost)
  override def apiBaseUri(instance: Instance): String = baseUri(apiHost)
  override def loaderBaseUri(instance: Instance) = baseUri(loaderHost)
  override def projectionBaseUri(instance: Instance) = baseUri(projectionHost)
  override def cropperBaseUri(instance: Instance): String = baseUri(cropperHost)
  override def metadataBaseUri(instance: Instance): String = baseUri(metadataHost)
  override def imgopsBaseUri(instance: Instance): String = baseUri(imgopsHost)
  override def usageBaseUri(instance: Instance) = baseUri(usageHost)
  override def collectionsBaseUri(instance: Instance) = baseUri(collectionsHost)
  override def leasesBaseUri(instance: Instance) = baseUri(leasesHost)
  override def authBaseUri(instance: Instance) = baseUri(authHost)
  def thrallBaseUri(instance: Instance) = baseUri(thrallHost)

  val guardianWitnessBaseUri: String = "https://n0ticeapis.com"

  override def corsAllowedDomains(instance: Instance): Set[String] = corsAllowedOrigins.map(baseUri) + kahunaBaseUri(instance) + apiBaseUri(instance) + thrallBaseUri(instance)

  val redirectUriParam = "redirectUri"
  val redirectUriPlaceholder = s"{?$redirectUriParam}"
  override def loginUriTemplate(instance: Instance) = s"${authBaseUri(instance)}/login$redirectUriPlaceholder"

  private def baseUri(host: String) = s"https://$host"
}

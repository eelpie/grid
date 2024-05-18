package com.gu.mediaservice.lib.config

import play.api.mvc.RequestHeader

trait Services {

  def kahunaBaseUri(request: RequestHeader): String

  def apiBaseUri(request: RequestHeader): String

  def loaderBaseUri(request: RequestHeader): String

  def projectionBaseUri(request: RequestHeader): String

  def cropperBaseUri(request: RequestHeader): String

  def metadataBaseUri(request: RequestHeader): String

  def imgopsBaseUri(request: RequestHeader): String

  def usageBaseUri(request: RequestHeader): String

  def collectionsBaseUri(request: RequestHeader): String

  def leasesBaseUri(request: RequestHeader): String

  def authBaseUri(request: RequestHeader): String

  def guardianWitnessBaseUri: String

  def corsAllowedDomains(request: RequestHeader): Set[String]

  def redirectUriParam: String

  def redirectUriPlaceholder: String

  def loginUriTemplate(requestHeader: RequestHeader): String
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
  override def kahunaBaseUri(request: RequestHeader): String =  vhostServiceName("", request)

  override def apiBaseUri(request: RequestHeader): String=  vhostServiceName("media-api", request)

  override def loaderBaseUri(request: RequestHeader): String = vhostServiceName("image-loader", request)

  override def projectionBaseUri(request: RequestHeader): String = vhostServiceName("projection", request)

  override def cropperBaseUri(request: RequestHeader): String = vhostServiceName("cropper", request)

  override def metadataBaseUri(request: RequestHeader): String = vhostServiceName("metadata-editor", request)

  override def imgopsBaseUri(request: RequestHeader): String = vhostServiceName("imgops", request)

  override def usageBaseUri(request: RequestHeader): String = vhostServiceName("usage", request)

  override def collectionsBaseUri(request: RequestHeader): String = vhostServiceName("collections", request)

  override def leasesBaseUri(request: RequestHeader): String = vhostServiceName("leases", request)

  override def authBaseUri(request: RequestHeader): String = vhostServiceName("auth", request)

  private def thrallBaseUri(request: RequestHeader): String = vhostServiceName("thrall", request)

  val guardianWitnessBaseUri: String = "https://n0ticeapis.com"

  override def corsAllowedDomains(request: RequestHeader): Set[String] = Set(kahunaBaseUri(request), apiBaseUri(request), thrallBaseUri(request))

  val redirectUriParam = "redirectUri"
  val redirectUriPlaceholder = s"{?$redirectUriParam}"
  def loginUriTemplate(request: RequestHeader): String = s"${authBaseUri(request)}/login$redirectUriPlaceholder"

  private def vhostServiceName(serviceName: String, request: RequestHeader): String = {
    val vhostRootUrl = request.host
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

  override def kahunaBaseUri(request: RequestHeader) = baseUri(kahunaHost)
  override def apiBaseUri(request: RequestHeader): String = baseUri(apiHost)
  override def loaderBaseUri(request: RequestHeader) = baseUri(loaderHost)
  override def projectionBaseUri(request: RequestHeader) = baseUri(projectionHost)
  override def cropperBaseUri(request: RequestHeader): String = baseUri(cropperHost)
  override def metadataBaseUri(request: RequestHeader): String = baseUri(metadataHost)
  override def imgopsBaseUri(request: RequestHeader): String = baseUri(imgopsHost)
  override def usageBaseUri(request: RequestHeader) = baseUri(usageHost)
  override def collectionsBaseUri(request: RequestHeader) = baseUri(collectionsHost)
  override def leasesBaseUri(request: RequestHeader) = baseUri(leasesHost)
  override def authBaseUri(request: RequestHeader) = baseUri(authHost)
  def thrallBaseUri(request: RequestHeader) = baseUri(thrallHost)

  val guardianWitnessBaseUri: String = "https://n0ticeapis.com"

  override def corsAllowedDomains(request: RequestHeader): Set[String] = corsAllowedOrigins.map(baseUri) + kahunaBaseUri(request) + apiBaseUri(request) + thrallBaseUri(request)

  val redirectUriParam = "redirectUri"
  val redirectUriPlaceholder = s"{?$redirectUriParam}"
  override def loginUriTemplate(request: RequestHeader) = s"${authBaseUri(request)}/login$redirectUriPlaceholder"

  private def baseUri(host: String) = s"https://$host"
}

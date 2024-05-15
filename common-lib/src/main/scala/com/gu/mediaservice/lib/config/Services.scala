package com.gu.mediaservice.lib.config

import play.api.mvc.RequestHeader

trait Services {

  def kahunaBaseUri: String

  def apiBaseUri(request: RequestHeader): String

  def loaderBaseUri: String

  def projectionBaseUri: String

  def cropperBaseUri(request: RequestHeader): String

  def metadataBaseUri(request: RequestHeader): String

  def imgopsBaseUri(request: RequestHeader): String

  def usageBaseUri: String

  def collectionsBaseUri: String

  def leasesBaseUri: String

  def authBaseUri: String

  def guardianWitnessBaseUri: String

  def corsAllowedDomains(request: RequestHeader): Set[String]

  def redirectUriParam: String

  def redirectUriPlaceholder: String

  def loginUriTemplate: String

  def apiInternalBaseUri: String

  def collectionsInternalBaseUri: String

  def cropperInternalBaseUri: String

  def leasesInternalBaseUri: String

  def metadataInternalBaseUri: String

  def projectionInternalBaseUri: String

  def usageInternalBaseUri: String
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
  val kahunaBaseUri: String = rootUrl

  override def apiBaseUri(request: RequestHeader): String=  vhostServiceName("media-api", request)

  val loaderBaseUri: String = subpathedServiceBaseUri("image-loader")

  val projectionBaseUri: String = loaderBaseUri

  override def cropperBaseUri(request: RequestHeader): String = vhostServiceName("cropper", request)

  override def metadataBaseUri(request: RequestHeader): String = vhostServiceName("metadata-editor", request)

  override def imgopsBaseUri(request: RequestHeader): String = vhostServiceName("imgops", request)

  val usageBaseUri: String =subpathedServiceBaseUri("usage")

  val collectionsBaseUri: String = subpathedServiceBaseUri("collections")

  val leasesBaseUri: String = subpathedServiceBaseUri("leases")

  val authBaseUri: String = subpathedServiceBaseUri("auth")

  private val thrallBaseUri: String =  subpathedServiceBaseUri("thrall")

  val guardianWitnessBaseUri: String = "https://n0ticeapis.com"

  override def corsAllowedDomains(request: RequestHeader): Set[String] = Set(kahunaBaseUri, apiBaseUri(request), thrallBaseUri)

  val redirectUriParam = "redirectUri"
  val redirectUriPlaceholder = s"{?$redirectUriParam}"
  val loginUriTemplate = s"$authBaseUri/login$redirectUriPlaceholder"

  val apiInternalBaseUri: String = internalServiceBaseUri("media-api", 9000)
  val collectionsInternalBaseUri: String = internalServiceBaseUri("collections", 9000)
  val cropperInternalBaseUri: String = internalServiceBaseUri("cropper", 9000)
  val leasesInternalBaseUri: String = internalServiceBaseUri("leases", 9000)
  val metadataInternalBaseUri: String = internalServiceBaseUri("metadata-editor", 9000)
  val projectionInternalBaseUri: String = internalServiceBaseUri("projection", 9000)
  val usageInternalBaseUri: String = internalServiceBaseUri("usages", 9000)

  private def vhostServiceName(serviceName: String, request: RequestHeader): String = {
    val vhostRootUrl = request.host
    s"https://$vhostRootUrl/" + serviceName
  }

  private def subpathedServiceBaseUri(serviceName: String): String = s"$rootUrl/$serviceName"

  private def internalServiceBaseUri(host: String, port: Int) = s"http://$host:$port"

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

  val kahunaBaseUri = baseUri(kahunaHost)
  override def apiBaseUri(request: RequestHeader): String = baseUri(apiHost)
  val loaderBaseUri = baseUri(loaderHost)
  val projectionBaseUri = baseUri(projectionHost)
  override def cropperBaseUri(request: RequestHeader): String = baseUri(cropperHost)
  override def metadataBaseUri(request: RequestHeader): String = baseUri(metadataHost)
  override def imgopsBaseUri(request: RequestHeader): String = baseUri(imgopsHost)
  val usageBaseUri = baseUri(usageHost)
  val collectionsBaseUri = baseUri(collectionsHost)
  val leasesBaseUri = baseUri(leasesHost)
  val authBaseUri = baseUri(authHost)
  val thrallBaseUri = baseUri(thrallHost)

  val guardianWitnessBaseUri: String = "https://n0ticeapis.com"

  override def corsAllowedDomains(request: RequestHeader): Set[String] = corsAllowedOrigins.map(baseUri) + kahunaBaseUri + apiBaseUri(request) + thrallBaseUri

  val redirectUriParam = "redirectUri"
  val redirectUriPlaceholder = s"{?$redirectUriParam}"
  val loginUriTemplate = s"$authBaseUri/login$redirectUriPlaceholder"

  private def baseUri(host: String) = s"https://$host"

  val apiInternalBaseUri: String = baseUri(apiHost)
  val collectionsInternalBaseUri: String = collectionsBaseUri
  val cropperInternalBaseUri: String = baseUri(cropperHost)
  val leasesInternalBaseUri: String = leasesBaseUri
  val metadataInternalBaseUri: String = baseUri(metadataHost)
  val projectionInternalBaseUri: String = projectionBaseUri
  val usageInternalBaseUri: String = usageBaseUri
}

package com.gu.mediaservice.lib.config

import play.api.mvc.RequestHeader

trait Services {

  def kahunaBaseUri: String

  def apiBaseUri(request: RequestHeader): String

  def loaderBaseUri(request: RequestHeader): String

  def projectionBaseUri(request: RequestHeader): String

  def cropperBaseUri(request: RequestHeader): String

  def metadataBaseUri(request: RequestHeader): String

  def imgopsBaseUri(request: RequestHeader): String

  def usageBaseUri(request: RequestHeader): String

  def collectionsBaseUri(request: RequestHeader): String

  def leasesBaseUri(request: RequestHeader): String

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

protected class SingleHostServices(val rootUrl: String) extends Services {
  val kahunaBaseUri: String = rootUrl

  override def apiBaseUri(request: RequestHeader): String=  vhostServiceName("media-api", request)

  override def loaderBaseUri(request: RequestHeader): String = vhostServiceName("image-loader", request)

  override def projectionBaseUri(request: RequestHeader): String = vhostServiceName("projection", request)

  override def cropperBaseUri(request: RequestHeader): String = vhostServiceName("cropper", request)

  override def metadataBaseUri(request: RequestHeader): String = vhostServiceName("metadata-editor", request)

  override def imgopsBaseUri(request: RequestHeader): String = vhostServiceName("imgops", request)

  override def usageBaseUri(request: RequestHeader): String = vhostServiceName("usages", request)

  override def collectionsBaseUri(request: RequestHeader): String = vhostServiceName("collections", request)

  override def leasesBaseUri(request: RequestHeader): String = vhostServiceName("leases", request)

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


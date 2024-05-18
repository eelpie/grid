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


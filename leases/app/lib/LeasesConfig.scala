package lib

import com.gu.mediaservice.lib.argo.model.Link
import com.gu.mediaservice.lib.config.{CommonConfig, GridConfigResources}
import play.api.mvc.{AnyContent, Request, RequestHeader}

import java.net.URI
import scala.util.Try

class LeasesConfig(resources: GridConfigResources) extends CommonConfig(resources) {
  val leasesTable = string("dynamo.tablename.leasesTable")

  def rootUri: RequestHeader => String = services.leasesBaseUri

  private def uri(u: String) = URI.create(u)

  private def leasesUri(request: Request[AnyContent]) = uri(s"${rootUri(request)}/leases")

  def leaseUri(leaseId: String)(request: Request[AnyContent]): Option[URI] = Try { URI.create(s"${leasesUri(request)}/$leaseId") }.toOption
  def leasesMediaUri(mediaId: String)(request: Request[AnyContent]) = Try { URI.create(s"${leasesUri(request)}/media/$mediaId") }.toOption

  private def mediaApiUri(id: String)(implicit r: Request[AnyContent]) = s"${services.apiBaseUri(r)}/images/$id"
  def mediaApiLink(id: String)(implicit r: Request[AnyContent]) = Link("media", mediaApiUri(id))
}

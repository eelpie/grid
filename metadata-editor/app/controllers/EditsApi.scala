package controllers

import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.argo.model.Link
import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.config.{InstanceForRequest, RuntimeUsageRightsConfig, UsageRightsConfigProvider}
import com.gu.mediaservice.model._
import lib.EditsConfig
import model.UsageRightsProperty
import play.api.libs.json._
import play.api.mvc.{AnyContent, BaseController, ControllerComponents, Request}

import scala.concurrent.ExecutionContext

class EditsApi(auth: Authentication, config: EditsConfig,
               override val controllerComponents: ControllerComponents)(implicit val ec: ExecutionContext)
  extends BaseController with ArgoHelpers with InstanceForRequest {


    // TODO: add links to the different responses esp. to the reference image
  def indexResponse()(implicit instance: Instance) = {
    val indexData = Map("description" -> "This is the Metadata Editor Service")
    val indexLinks = List(
      Link("edits",             s"${config.rootUri(instance)}/metadata/{id}"),
      Link("archived",          s"${config.rootUri(instance)}/metadata/{id}/archived"),
      Link("labels",            s"${config.rootUri(instance)}/metadata/{id}/labels"),
      Link("usageRights",       s"${config.rootUri(instance)}/metadata/{id}/usage-rights"),
      Link("metadata",          s"${config.rootUri(instance)}/metadata/{id}/metadata"),
      Link("usage-rights-list", s"${config.rootUri(instance)}/usage-rights/categories")
    )
    respond(indexData, indexLinks)
  }

  def index = auth { request =>
    implicit val instance: Instance = instanceOf(request)
    indexResponse()
  }

  def usageRightsResponse()(implicit request: Request[AnyContent]) = {
    val usageRights = config.applicableUsageRights.toList

    val usageRightsData = usageRights
          .map(u => CategoryResponse.fromUsageRights(u, config))

    respond(usageRightsData)
  }

  def getUsageRights = auth { request =>
    usageRightsResponse()(request)
  }
}

case class CategoryResponse(
  value: String,
  name: String,
  cost: String,
  description: String,
  defaultRestrictions: Option[String],
  caution: Option[String],
  properties: List[UsageRightsProperty] = List(),
  usageRestrictions: Option[String],
  usageSpecialInstructions: Option[String]
)
object CategoryResponse {
  // I'd like to have an override of the `apply`, but who knows how you do that
  // with the JSON parsing stuff

  def fromUsageRights(u: UsageRightsSpec, config: EditsConfig) = CategoryResponse (
      value = u.category,
      name = u.name(config),
      cost = u.defaultCost.getOrElse(Pay).toString,
      description = u.description(config),
      defaultRestrictions = u.defaultRestrictions,
      caution = u.caution,
      properties = UsageRightsProperty.getPropertiesForSpec(u, config.usageRightsConfig),
      usageRestrictions = config.customUsageRestrictions.get(u.category),
      usageSpecialInstructions = config.customSpecialInstructions.get(u.category)
  )

  implicit val categoryResponseWrites: Writes[CategoryResponse] = Json.writes[CategoryResponse]

}

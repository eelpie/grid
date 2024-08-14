package controllers

import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.aws.{DynamoDB, NoItemFound}
import com.gu.mediaservice.lib.config.InstanceForRequest
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, RequestHeader}
import com.gu.mediaservice.model._
import com.gu.mediaservice.syntax.MessageSubjects
import lib._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}


class SyndicationController(auth: Authentication,
                            val editsStore: EditsStore,
                            val syndicationStore: SyndicationStore,
                            val notifications: Notifications,
                            val config: EditsConfig,
                            override val controllerComponents: ControllerComponents)(implicit val ec: ExecutionContext)
  extends BaseController with Syndication with MessageSubjects with ArgoHelpers with EditsResponse
    with InstanceForRequest {

  override val metadataBaseUri: RequestHeader => String = config.services.metadataBaseUri

  def getPhotoshoot(id: String) = auth.async {
    editsStore.jsonGet(id, Edits.Photoshoot).map(dynamoEntry => {
      (dynamoEntry \ Edits.Photoshoot).toOption match {
        case Some(photoshoot) => respond(photoshoot.as[Photoshoot])
        case None => respondNotFound("No photoshoot found")
      }
    }) recover {
      case NoItemFound => respondNotFound("No photoshoot found")
    }
  }

  def setPhotoshoot(id: String) = auth.async(parse.json) { req => {
    (req.body \ "data").asOpt[Photoshoot].map(photoshoot =>
      setPhotoshootAndPublish(id, photoshoot, instanceOf(req))
        .map(photoshoot => respond(photoshoot))
    )
    .getOrElse(
      Future.successful(respondError(BadRequest, "invalid-form-data", "Invalid form data"))
    )
  }}

  def deletePhotoshoot(id: String) = auth.async { request =>
    deletePhotoshootAndPublish(id, instanceOf(request)).map(_ => Accepted)
  }

  def getSyndication(id: String): Action[AnyContent] = auth.async {
    getSyndicationForImage(id)
    .map {
      case Some(rights) => respond(rights)
      // If no rights, send a 404 - no syndication rights for this id.  Either id is duff, or it really has none.
      case None => NotFound
    }
  }

  def setSyndication(id: String): Action[JsValue] = auth.async(parse.json) { req => {
    (req.body \ "data").asOpt[SyndicationRights].map(syndicationRight => {
      setSyndicationAndPublish(id, syndicationRight, instanceOf(req))
        .map(syndicationRight => respond(syndicationRight))
    }).getOrElse(Future.successful(respondError(BadRequest, "invalid-form-data", "Invalid form data")))
  }}

  def deleteSyndication(id: String): Action[AnyContent] = auth.async { request =>
    deleteSyndicationAndPublish(id, instanceOf(request)).map(_ => Accepted)
  }

}

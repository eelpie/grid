package controllers

import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.auth.Authentication.getIdentity
import com.gu.mediaservice.lib.aws.{DynamoDB, NoItemFound, UpdateMessage}
import com.gu.mediaservice.lib.collections.CollectionsManager
import com.gu.mediaservice.lib.config.InstanceForRequest
import com.gu.mediaservice.lib.net.{URI => UriOps}
import com.gu.mediaservice.model.{ActionData, Collection, Instance}
import com.gu.mediaservice.syntax.MessageSubjects
import lib.{CollectionsConfig, Notifications}
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.{BaseController, ControllerComponents}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class ImageCollectionsController(authenticated: Authentication, config: CollectionsConfig, notifications: Notifications,
                                 override val controllerComponents: ControllerComponents)
  extends BaseController with MessageSubjects with ArgoHelpers with InstanceForRequest {

  import CollectionsManager.onlyLatest

  val dynamo = new DynamoDB[Collection](config, config.imageCollectionsTable)

  def getCollections(id: String) = authenticated.async { req =>
    dynamo.listGet(id, "collections").map { collections =>
      respond(onlyLatest(collections))
    } recover {
      case NoItemFound => respondNotFound("No collections found")
    }
  }

  def addCollection(id: String) = authenticated.async(parse.json) { req =>
    (req.body \ "data").asOpt[List[String]].map { path =>
      val collection = Collection.build(path, ActionData(getIdentity(req.user), DateTime.now()))
      dynamo.listAdd(id, "collections", collection)
        .map(publish(id, instanceOf(req)))
        .map(cols => respond(collection))
    } getOrElse Future.successful(respondError(BadRequest, "invalid-form-data", "Invalid form data"))
  }


  def removeCollection(id: String, collectionString: String) = authenticated.async { req =>
    val path = CollectionsManager.uriToPath(UriOps.encodePlus(collectionString))
    // We do a get to be able to find the index of the current collection, then remove it.
    // Given that we're using Dynamo Lists this seemed like a decent way to do it.
    // Dynamo Lists, like other lists do respect order.
    dynamo.listGet(id, "collections") flatMap { collections =>
      CollectionsManager.findIndexes(path, collections) match {
        case Nil =>
          Future.successful(respondNotFound(s"Collection $collectionString not found"))
        case indexes =>
          dynamo.listRemoveIndexes(id, "collections", indexes)
            .map(publish(id, instanceOf(req)))
            .map(cols => respond(cols))
      }
    } recover {
      case NoItemFound => respondNotFound("No collections found")
    }
  }

  def publish(id: String, instance: Instance)(collections: List[Collection]): List[Collection] = {
    val onlyLatestCollections = onlyLatest(collections)
    val updateMessage = UpdateMessage(subject = SetImageCollections, id = Some(id), collections = Some(onlyLatestCollections), instance = instance)
    notifications.publish(updateMessage)
    onlyLatestCollections
  }

}



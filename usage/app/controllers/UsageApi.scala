package controllers

import java.net.URI
import com.gu.contentapi.client.model.ItemQuery
import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.argo.model.{EntityResponse, Link, Action => ArgoAction}
import com.gu.mediaservice.lib.auth.{Authentication, Authorisation}
import com.gu.mediaservice.lib.aws.UpdateMessage
import com.gu.mediaservice.lib.config.InstanceForRequest
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap}
import com.gu.mediaservice.lib.play.RequestLoggingFilter
import com.gu.mediaservice.lib.usage.UsageBuilder
import com.gu.mediaservice.model.usage.{MediaUsage, SyndicatedUsageStatus, Usage, UsageNotice, UsageStatus}
import com.gu.mediaservice.syntax.MessageSubjects
import lib._
import model._
import play.api.libs.json.{JsArray, JsError, JsValue, Json}
import play.api.mvc._
import play.utils.UriEncoding
import rx.lang.scala.Subject

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class UsageApi(
  auth: Authentication,
  authorisation: Authorisation,
  usageTable: UsageTable,
  usageGroupOps: UsageGroupOps,
  notifications: Notifications,
  config: UsageConfig,
  usageApiSubject: Subject[WithLogMarker[UsageGroup]],
  override val controllerComponents: ControllerComponents,
  playBodyParsers: PlayBodyParsers
)(
  implicit val ec: ExecutionContext
) extends BaseController with MessageSubjects with ArgoHelpers with InstanceForRequest {

  private val AuthenticatedAndAuthorisedToDelete = auth andThen authorisation.CommonActionFilters.authorisedForDeleteCropsOrUsages

  private def wrapUsage(usage: Usage)(request: Request[AnyContent]): EntityResponse[Usage] = {
    EntityResponse(
      uri = usageUri(usage.id)(request),
      data = usage
    )
  }

  private def usageUri(usageId: String)(request: Request[AnyContent]): Option[URI] = {
    val encodedUsageId = UriEncoding.encodePathSegment(usageId, "UTF-8")
    Try { URI.create(s"${config.usageUri(request)}/usages/$encodedUsageId") }.toOption
  }

  def indexResponse()(request: Request[AnyContent]) = {
    val indexData = Map("description" -> "This is the Usage Recording service")
    val indexLinks = List(
      Link("usages-by-media", s"${config.usageUri(request)}/usages/media/{id}"),
      Link("usages-by-id", s"${config.usageUri(request)}/usages/{id}")
    )

    val printPostUri = URI.create(s"${config.usageUri(request)}/usages/print")
    val actions = List(
      ArgoAction("print-usage", printPostUri, "POST")
    )

    respond(indexData, indexLinks, actions)
  }
  def index = auth { request =>
    indexResponse()(request)
  }

  def forUsage(usageId: String) = auth.async { req =>
    implicit val logMarker: LogMarker = MarkerMap(
      "requestType" -> "get-usage",
      "requestId" -> RequestLoggingFilter.getRequestId(req),
      "usageId" -> usageId,
    )
    logger.info(logMarker, s"Request for single usage $usageId")
    val usageFuture = usageTable.queryByUsageId(usageId)

    usageFuture.map[play.api.mvc.Result]((mediaUsageOption: Option[MediaUsage]) => {
      mediaUsageOption.foldLeft(
        respondNotFound("No usages found.")
      )((_, mediaUsage: MediaUsage) => {
        val usage = UsageBuilder.build(mediaUsage)
        val mediaId = mediaUsage.mediaId

        val uri = usageUri(usage.id)(req)
        val links = List(
          Link("media", s"${config.services.apiBaseUri(req)}/images/$mediaId"),
          Link("media-usage", s"${config.services.usageBaseUri(req)}/usages/media/$mediaId")
        )

        respond[Usage](data = usage, uri = uri, links = links)
      })
    }).recover { case error: Exception =>
      logger.error(logMarker, "UsageApi returned an error.", error)
      respondError(InternalServerError, "usage-retrieve-failed", error.getMessage)
    }

  }

  def forMedia(mediaId: String) = auth.async { req =>
    implicit val logMarker: LogMarker = MarkerMap(
      "requestType" -> "usages-for-media-id",
      "requestId" -> RequestLoggingFilter.getRequestId(req),
      "image-id" -> mediaId,
    )

    val usagesFuture = usageTable.queryByImageId(mediaId)

    usagesFuture.map[play.api.mvc.Result]((mediaUsages: List[MediaUsage]) => {
      val usages = mediaUsages.map(UsageBuilder.build)

      usages match {
        case Nil => respondNotFound("No usages found.")
        case _ =>
          val uri = Try { URI.create(s"${config.services.usageBaseUri(req)}/usages/media/$mediaId") }.toOption
          val links = List(
            Link("media", s"${config.services.apiBaseUri(req)}/images/$mediaId")
          )

          respondCollection[EntityResponse[Usage]](
            uri = uri,
            links = links,
            data = usages.map(u => wrapUsage(u)(req))
          )
      }
    }).recover {
      case error: BadInputException =>
        logger.error(logMarker, "UsageApi returned an error.", error)
        respondError(BadRequest, "image-usage-retrieve-failed", error.getMessage)
      case error: Exception =>
        logger.error(logMarker, "UsageApi returned an error.", error)
        respondError(InternalServerError, "image-usage-retrieve-failed", error.getMessage)
    }
  }

  val maxPrintRequestLength: Int = 1024 * config.maxPrintRequestLengthInKb
  val setPrintRequestBodyParser: BodyParser[JsValue] = playBodyParsers.json(maxLength = maxPrintRequestLength)

  def setPrintUsages = auth(setPrintRequestBodyParser) { req => {

    val printUsageRequestResult = req.body.validate[PrintUsageRequest]
    printUsageRequestResult.fold(
      e => {
        respondError(BadRequest, "print-usage-request-parse-failed", JsError.toJson(e).toString)
      },
      printUsageRequest => {
        implicit val logMarker: LogMarker = MarkerMap(
          "requestType" -> "set-print-usages",
          "requestId" -> RequestLoggingFilter.getRequestId(req),
        )
        val usageGroups = usageGroupOps.build(printUsageRequest.printUsageRecords)
        usageGroups.map(WithLogMarker.includeUsageGroup).foreach(usageApiSubject.onNext)

        Accepted
      }
    )
  }}

  def setSyndicationUsages() = auth(parse.json) { req => {

    val syndicationUsageRequest = (req.body \ "data").validate[SyndicationUsageRequest]
    syndicationUsageRequest.fold(
      e => respondError(
        BadRequest,
        errorKey = "syndication-usage-parse-failed",
        errorMessage = JsError.toJson(e).toString
      ),
      sur => {
        implicit val logMarker: LogMarker = MarkerMap(
          "requestType" -> "set-syndication-usages",
          "requestId" -> RequestLoggingFilter.getRequestId(req),
          "image-id" -> sur.mediaId,
        ) ++ apiKeyMarkers(req.user.accessor)

        logger.info(logMarker, "recording syndication usage")
        val group = usageGroupOps.build(sur)
        usageApiSubject.onNext(WithLogMarker.includeUsageGroup(group))
        Accepted
      }
    )
  }}

  def setFrontUsages() = auth(parse.json) { req => {

    val request = (req.body \ "data").validate[FrontUsageRequest]
    request.fold(
      e => respondError(
        BadRequest,
        errorKey = "front-usage-parse-failed",
        errorMessage = JsError.toJson(e).toString
      ),
      fur => {
        implicit val logMarker: LogMarker = MarkerMap(
          "requestType" -> "set-front-usages",
          "requestId" -> RequestLoggingFilter.getRequestId(req),
          "image-id" -> fur.mediaId,
        ) ++ apiKeyMarkers(req.user.accessor)
        logger.info(logMarker, "recording front usage")
        val group = usageGroupOps.build(fur)
        usageApiSubject.onNext(WithLogMarker.includeUsageGroup(group))
        Accepted
      }
    )
  }}

  def setDownloadUsages() = auth(parse.json) { req => {

    val request = (req.body \ "data").validate[DownloadUsageRequest]
    request.fold(
      e => respondError(
        BadRequest,
        errorKey = "download-usage-parse-failed",
        errorMessage = JsError.toJson(e).toString
      ),
      usageRequest => {
        implicit val logMarker: LogMarker = MarkerMap(
          "requestType" -> "set-download-usages",
          "requestId" -> RequestLoggingFilter.getRequestId(req),
          "image-id" -> usageRequest.mediaId,
        ) ++ apiKeyMarkers(req.user.accessor)
        logger.info(logMarker, "recording download usage")
        val group = usageGroupOps.build(usageRequest)
        usageApiSubject.onNext(WithLogMarker.includeUsageGroup(group))
        Accepted
      }
    )
  }}

  def updateUsageStatus(mediaId: String, usageId: String) = auth.async(parse.json) {req => {
    val request = (req.body \ "data").validate[UsageStatus]
    request.fold(
      e => Future.successful(
        respondError(
          BadRequest,
          errorKey = "update-image-usage-status-failed",
          errorMessage = JsError.toJson(e).toString()
        )
      ),
      usageStatus => {
        implicit val logMarker: LogMarker = MarkerMap(
          "requestType" -> "update-usage-status",
          "requestId" -> RequestLoggingFilter.getRequestId(req),
          "usageStatus" -> usageStatus.toString,
          "image-id" -> mediaId,
          "usage-id" -> usageId,
        ) ++ apiKeyMarkers(req.user.accessor)
        logger.info(logMarker, "recording usage status  update")

        usageTable.queryByUsageId(usageId).map {
          case Some(mediaUsage) =>
            val updatedStatusMediaUsage = mediaUsage.copy(status = usageStatus)
            usageTable.update(updatedStatusMediaUsage)
            val usageNotice = UsageNotice(mediaId,
              JsArray(Seq(Json.toJson(UsageBuilder.build(updatedStatusMediaUsage)))),
              instanceOf(req)
            )
            val updateMessage = UpdateMessage(
              subject = UpdateUsageStatus, id = Some(mediaId),
              usageNotice = Some(usageNotice),
              instance = instanceOf(req)
            )
            notifications.publish(updateMessage)
            Ok
          case None =>
            NotFound
        }
      }
    )
  }}

  def deleteSingleUsage(mediaId: String, usageId: String) = AuthenticatedAndAuthorisedToDelete.async { req =>
    implicit val logMarker: LogMarker = MarkerMap(
      "requestType" -> "delete-usage",
      "requestId" -> RequestLoggingFilter.getRequestId(req),
      "image-id" -> mediaId,
      "usage-id" -> usageId,
    )

    usageTable.queryByUsageId(usageId).map {
      case Some(mediaUsage) =>
        usageTable.deleteRecord(mediaUsage)
        val updateMessage = UpdateMessage(subject = DeleteSingleUsage, id = Some(mediaId), usageId = Some(usageId), instance = instanceOf(req))
        notifications.publish(updateMessage)
        Ok
      case None =>
        NotFound
    }

  }

  def deleteUsages(mediaId: String) = AuthenticatedAndAuthorisedToDelete.async { req =>
    implicit val logMarker: LogMarker = MarkerMap(
      "requestType" -> "delete-usages",
      "requestId" -> RequestLoggingFilter.getRequestId(req),
      "image-id" -> mediaId,
    )
    usageTable.queryByImageId(mediaId).map(usages => {
      usages.foreach(usageTable.deleteRecord)
    }).recover {
      case error: BadInputException =>
        logger.warn(logMarker, "UsageApi returned an error.", error)
        respondError(BadRequest, "image-usage-delete-failed", error.getMessage)
      case error: Exception =>
        logger.error(logMarker, "UsageApi returned an error.", error)
        respondError(InternalServerError, "image-usage-delete-failed", error.getMessage)
    }

    val updateMessage = UpdateMessage(subject = DeleteUsages, id = Some(mediaId), instance = instanceOf(req))
    notifications.publish(updateMessage)
    Future.successful(Ok)
  }

}

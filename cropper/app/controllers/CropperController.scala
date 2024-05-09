package controllers

import _root_.play.api.libs.json._
import _root_.play.api.mvc.{BaseController, ControllerComponents}
import com.gu.mediaservice.GridClient
import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.argo.model.Link
import com.gu.mediaservice.lib.auth.Authentication.Principal
import com.gu.mediaservice.lib.auth.Permissions.{DeleteCropsOrUsages, PrincipalFilter}
import com.gu.mediaservice.lib.auth._
import com.gu.mediaservice.lib.aws.UpdateMessage
import com.gu.mediaservice.lib.imaging.ExportResult
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap}
import com.gu.mediaservice.lib.play.RequestLoggingFilter
import com.gu.mediaservice.model._
import com.gu.mediaservice.syntax.MessageSubjects
import lib._
import model._
import org.joda.time.DateTime

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal


case object InvalidSource extends Exception("Invalid source URI, not a media API URI")
case object ImageNotFound extends Exception("No such image found")
case object ApiRequestFailed extends Exception("Failed to fetch the source")

class CropperController(auth: Authentication, crops: Crops, store: CropStore, notifications: Notifications,
                        config: CropperConfig,
                        override val controllerComponents: ControllerComponents,
                        authorisation: Authorisation,
                        gridClient: GridClient)(implicit val ec: ExecutionContext)
  extends BaseController with MessageSubjects with ArgoHelpers {

  // Stupid name clash between Argo and Play
  import com.gu.mediaservice.lib.argo.model.{Action => ArgoAction}

  val AuthenticatedAndAuthorisedToDeleteCrops = auth andThen authorisation.CommonActionFilters.authorisedForDeleteCropsOrUsages

  val indexResponse = {
    val indexData = Map("description" -> "This is the Cropper Service")
    val indexLinks = List(
      Link("crop", s"${config.rootUri}/crops")
    )
    respond(indexData, indexLinks)
  }

  def index = auth { indexResponse }

  def export = auth.async(parse.json) { httpRequest =>
    httpRequest.body.validate[ExportRequest] map { exportRequest =>
      implicit val logMarker: LogMarker = MarkerMap(
        "requestType" -> "export",
        "requestId" -> RequestLoggingFilter.getRequestId(httpRequest)
      )

      val user = httpRequest.user
      val onBehalfOfPrincipal = auth.getOnBehalfOfPrincipal(user)

      executeRequest(exportRequest, user, onBehalfOfPrincipal).map { case (imageId, export) =>

        val cropJson = Json.toJson(export).as[JsObject]
        val updateMessage = UpdateMessage(subject = UpdateImageExports, id = Some(imageId), crops = Some(Seq(export)))
        notifications.publish(updateMessage)

        Ok(cropJson).as(ArgoMediaType)

      } recover {
        case InvalidSource =>
          logger.error(logMarker, InvalidSource.getMessage)
          respondError(BadRequest, "invalid-source", InvalidSource.getMessage)
        case ImageNotFound =>
          logger.error(logMarker, ImageNotFound.getMessage)
          respondError(BadRequest, "image-not-found", ImageNotFound.getMessage)
        case InvalidImage =>
          logger.error(logMarker, InvalidImage.getMessage)
          respondError(BadRequest, "invalid-image", InvalidImage.getMessage)
        case MissingSecureSourceUrl =>
          logger.error(logMarker, MissingSecureSourceUrl.getMessage)
          respondError(BadRequest, "no-source-image", MissingSecureSourceUrl.getMessage)
        case InvalidCropRequest =>
          logger.error(logMarker, InvalidCropRequest.getMessage)
          respondError(BadRequest, "invalid-crop", InvalidCropRequest.getMessage)
        case ApiRequestFailed =>
          logger.error(logMarker, ApiRequestFailed.getMessage)
          respondError(BadGateway, "api-failed", ApiRequestFailed.getMessage)
        case NonFatal(e) =>
          logger.error(logMarker, s"export failed with exception", e)
          respondError(InternalServerError, "unknown-error", e.getMessage)
      }
    } recoverTotal {
      case e =>
        val validationErrors = for {
          (_, errors)  <- e.errors
          errorDetails <- errors
        } yield errorDetails.message
        val errorMessage = validationErrors.headOption getOrElse "Invalid export request"
        Future.successful(respondError(BadRequest, "bad-request", errorMessage))
    }
  }

  private val canDeleteCrops: PrincipalFilter = authorisation.hasPermissionTo(DeleteCropsOrUsages)

  private def downloadExportLink(imageId: String, exportId: String, width: Int) = Link(s"crop-download-$exportId-$width", s"${config.apiUri}/images/$imageId/export/$exportId/asset/$width/download")

  def getCrops(id: String) = auth.async { httpRequest =>

    implicit val logMarker: LogMarker = MarkerMap(
      "requestType" -> "getCrops",
      "requestId" -> RequestLoggingFilter.getRequestId(httpRequest),
      "imageId" -> id
    )

    logger.info(logMarker, s"getting crops for $id")

    store.listCrops(id) map (_.toList) map { crops =>
      val deleteCropsAction =
        ArgoAction("delete-crops", URI.create(s"${config.rootUri}/crops/$id"), "DELETE")

      lazy val cropDownloadLinks = for {
        crop <- crops
        asset <- crop.assets
        dimensions <- asset.dimensions
        width = dimensions.width
        cropId <- crop.id
      } yield downloadExportLink(id, cropId, width)

      val links = (for {
        crop <- crops.headOption
        link = Link("image", crop.specification.uri)
      } yield {
        if (config.canDownloadCrop) {
          link :: cropDownloadLinks
        } else List(link)
      }) getOrElse List()

      if(canDeleteCrops(httpRequest.user) && crops.nonEmpty) {
        respond(crops, links, List(deleteCropsAction))
      } else {
        respond(crops, links)
      }
    }
  }

  def deleteCrops(id: String) = AuthenticatedAndAuthorisedToDeleteCrops.async { httpRequest =>
    implicit val logMarker: LogMarker = MarkerMap(
      "requestType" -> "deleteCrops",
      "requestId" -> RequestLoggingFilter.getRequestId(httpRequest),
      "imageId" -> id
    )
    store.deleteCrops(id).map { _ =>
      val updateMessage = UpdateMessage(subject = DeleteImageExports, id = Some(id))
      notifications.publish(updateMessage)
      Accepted
    } recover {
      case _ => respondError(BadRequest, "deletion-error", "Could not delete crops")
    }
  }

  def executeRequest(
    exportRequest: ExportRequest, user: Principal, onBehalfOfPrincipal: Authentication.OnBehalfOfPrincipal
  )(implicit logMarker: LogMarker): Future[(String, Crop)] = {

    for {
      _ <- verify(isMediaApiUri(exportRequest.uri), InvalidSource)
      apiImage <- fetchSourceFromApi(exportRequest.uri, onBehalfOfPrincipal)
      _ <- verify(apiImage.valid, InvalidImage)
      // Image should always have dimensions, but we want to safely extract the Option
      dimensions <- ifDefined(apiImage.source.dimensions, InvalidImage)
      cropSpec = ExportRequest.toCropSpec(exportRequest, dimensions)
      _ <- verify(crops.isWithinImage(cropSpec.bounds, dimensions), InvalidCropRequest)
      crop = Crop.createFromCropSource(
        by = Some(Authentication.getIdentity(user)),
        timeRequested = Some(new DateTime()),
        specification = cropSpec
      )
      markersWithCropDetails = logMarker ++ Map("imageId" -> apiImage.id, "cropId" -> Crop.getCropId(cropSpec.bounds))
      ExportResult(id, masterSizing, sizings) <- crops.export(apiImage, crop)(markersWithCropDetails)
      finalCrop = Crop.createFromCrop(crop, masterSizing, sizings)
    } yield (id, finalCrop)
  }

  // TODO: lame, parse into URI object and compare host instead
  def isMediaApiUri(uri: String): Boolean = uri.startsWith(config.apiUri)

  private def fetchSourceFromApi(uri: String, onBehalfOfPrincipal: Authentication.OnBehalfOfPrincipal): Future[SourceImage] = {
    // Requesting source from API: http://york.local:32401/images/0e55872d4dc31120cedc4204b2f9545d305e89db"
    logger.info("Requesting source from API: " + uri)
    // Trim this back to a media id for compatibility with GridClient
    val mediaId = uri.split("/").lastOption.get // TODO not great; trace this up; how do other services get media ids?
    logger.info("Requesting source from GridClient using media id: " + mediaId)

    gridClient.getSourceImage(mediaId, onBehalfOfPrincipal)
  }

  def verify(cond: => Boolean, error: Throwable): Future[Unit] =
    if (cond) Future.successful(()) else Future.failed(error)

  def ifDefined[T](cond: => Option[T], error: Throwable): Future[T] =
    cond map Future.successful getOrElse Future.failed(error)

}

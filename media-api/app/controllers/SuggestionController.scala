package controllers

import com.gu.mediaservice.lib.ImageFields
import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.config.InstanceForRequest
import com.gu.mediaservice.lib.logging.{LogMarker, MarkerMap}
import com.gu.mediaservice.lib.play.RequestLoggingFilter
import com.gu.mediaservice.model.Instance
import lib.elasticsearch._
import play.api.mvc.{BaseController, ControllerComponents, Result}

import scala.concurrent.{ExecutionContext, Future}

class SuggestionController(auth: Authentication, elasticSearch: ElasticSearch,
                           override val controllerComponents: ControllerComponents)(implicit val ec: ExecutionContext)
  extends BaseController with ArgoHelpers with ImageFields with AggregateResponses with InstanceForRequest {

  def suggestMetadataCredit(q: Option[String], size: Option[Int]) = suggestion("suggestMetadataCredit", q, size)

  def suggestPhotoshoot(q: Option[String], size: Option[Int]) = suggestion(photoshootField("suggest"), q, size)

  // TODO: work with analysed fields
  // TODO: recover with HTTP error if invalid field
  // TODO: Add validation, especially if you use length
  def metadataSearch(field: String, q: Option[String]) = auth.async { implicit request =>
    implicit val instance: Instance = instanceOf(request)
    implicit val logMarker: LogMarker = MarkerMap(
      "requestType" -> "metadata-search",
      "requestId" -> RequestLoggingFilter.getRequestId(request),
      "fieldName" -> field,
    ) ++ RequestLoggingFilter.loggablePrincipal(request.user)

    validatedAggregateSearch(AggregateSearchParams(field, request))(elasticSearch.metadataSearch)
  }

  def editsSearch(field: String, q: Option[String]) = auth.async { request =>
    implicit val instance: Instance = instanceOf(request)
    implicit val logMarker: LogMarker = MarkerMap(
      "requestType" -> "edits-search",
      "requestId" -> RequestLoggingFilter.getRequestId(request),
      "fieldName" -> field,
    ) ++ RequestLoggingFilter.loggablePrincipal(request.user)

    validatedAggregateSearch(AggregateSearchParams(field, request))(elasticSearch.editsSearch)
  }

  private def validatedAggregateSearch(params: AggregateSearchParams)(
    search: AggregateSearchParams => Future[AggregateSearchResults]
  ): Future[Result] =
    AggregateSearchParams.validate(params) match {
      case Left(error) =>
        Future.successful(respondError(BadRequest, InvalidUriParams.errorKey, error.message))
      case Right(validParams) =>
        search(validParams).map(aggregateResponse)
    }

  private def suggestion(field: String, query: Option[String], size: Option[Int]) = auth.async { implicit request =>
    implicit val instance: Instance = instanceOf(request)

    query.flatMap(q => if (q.nonEmpty) Some(q) else None).map { q =>
      elasticSearch.completionSuggestion(field, q, size.getOrElse(10))
    }.getOrElse(
      Future.successful(CompletionSuggestionResults(List.empty))
    ).map(c => respondCollection(c.results))
  }

}

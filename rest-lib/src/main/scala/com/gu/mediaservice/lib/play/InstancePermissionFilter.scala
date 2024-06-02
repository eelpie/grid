package com.gu.mediaservice.lib.play

import akka.stream.Materializer
import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.auth.provider.KindeAuthenticationProvider
import com.gu.mediaservice.lib.config.InstanceForRequest
import com.typesafe.scalalogging.StrictLogging
import play.api.mvc.Results.Forbidden
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

class InstancePermissionFilter(override val mat: Materializer, auth: Authentication)(implicit ec: ExecutionContext)
  extends Filter with InstanceForRequest with StrictLogging {

  private val openPaths = Set("/management/healthcheck", "/login", "/oauthCallback", "/logout") // TODO logout should really be expressed as not instance on landing site's auth instance
  override def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    val uri = rh.uri
    if (openPaths.contains(uri)) {
      next(rh)

    } else {
      val instance = instanceOf(rh)
      // TODO the Future on the not authed response seems unnecessary? No real work seems to be wrapped
      val eventualResultOrPrincipal = auth.authenticationStatus(rh)
      eventualResultOrPrincipal.fold(eventualResult => {
        // Nothing to see here; we haven't created any side effects and should be able to skip
        logger.info(s"Allowing request to $instance with non principal")
        eventualResult.flatMap { _ =>
          next(rh)
        }
      }, principal => {
        // Check that the authenticated principal is attached to this instance
        val principalsInstances = principal.attributes.get(KindeAuthenticationProvider.instancesTypedKey).getOrElse(Seq.empty)
        if (principalsInstances.contains(instance.id)) {
          logger.debug("Allowing this request!")
          next(rh)

        } else {
          logger.warn(s"Blocking request $uri on instance $instance")
          Future.successful(Forbidden("You do not have permission to use this instance"))
        }
      }
      )
    }
  }

}

package com.gu.mediaservice.lib.auth.provider

import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.auth.provider.AuthenticationProvider.RedirectUri
import com.typesafe.scalalogging.StrictLogging
import org.openapitools.sdk.KindeClientSDK
import org.openapitools.sdk.enums.GrantType
import play.api.Configuration
import play.api.libs.ws.WSRequest
import play.api.mvc.{RequestHeader, Result}
import play.api.mvc.Results._

import java.util.UUID
import scala.concurrent.Future

class KindeAuthenticationProvider(config: Configuration) extends UserAuthenticationProvider with StrictLogging {
  /**
   * Establish the authentication status of the given request header. This can return an authenticated user or a number
   * of reasons why a user is not authenticated.
   *
   * @param request The request header containing cookies and other request headers that can be used to establish the
   *                authentication status of a request.
   * @return An authentication status expressing whether the
   */
  override def authenticateRequest(request: RequestHeader): AuthenticationStatus = {
    // Look for our cookie with we set in the auth app
    NotAuthenticated // TODO
  }

  /**
   * If this provider supports sending a user that is not authorised to a federated auth provider then it should
   * provide a function here to redirect the user. The function signature takes the the request and returns a result
   * which is likely a redirect to an external authentication system.
   */
  override def sendForAuthentication: Option[RequestHeader => Future[Result]] = Some {
    { requestHeader: RequestHeader =>
      val redirectUrl = config.get[String]("redirectUri")
      logger.info(s"Requesting Kinde redirect URI for redirectUri: $redirectUrl")

      val clientId = config.get[String]("clientId")
      val oauthRedirectUrl = config.get[String]("domain") +
        s"/oauth2/auth?response_type=code&client_id=$clientId&redirect_uri=$redirectUrl&scope=openid%20profile%20email&state=" + UUID.randomUUID().toString
      logger.info(s"Redirecting to Kinde OAuth URL: $oauthRedirectUrl")
      Future.successful(Redirect(oauthRedirectUrl))
    }
  }

  /**
   * If this provider supports sending a user that is not authorised to a federated auth provider then it should
   * provide a function here that deals with the return of a user from a federated provider. This should be
   * used to set a cookie or similar to ensure that a subsequent call to authenticateRequest will succeed. If
   * authentication failed then this should return an appropriate 4xx result.
   * The function should take the Play request header and the redirect URI that the user should be
   * sent to on successful completion of the authentication.
   */
  override def sendForAuthenticationCallback: Option[(RequestHeader, Option[RedirectUri]) => Future[Result]] = Some {
    { (requestHeader, _) =>
      logger.info("Got auth callback request header: " + requestHeader)
      Future.successful(play.api.mvc.Results.Ok("TODO"))
    }
  }

  /**
   * If this provider is able to clear user tokens (i.e. by clearing cookies) then it should provide a function to
   * do that here which will be used to log users out and also if the token is invalid.
   * This function takes the request header and a result to modify and returns the modified result.
   */
  override def flushToken: Option[(RequestHeader, Result) => Result] = ???

  /**
   * A function that allows downstream API calls to be made using the credentials of the current principal.
   * It is recommended that any data required for this downstream request enrichment is put into the principal's
   * attribute map when the principal is created in the authenticateRequest call.
   *
   * @param principal The principal for the current request
   * @return Either a function that adds appropriate authentication headers to a WSRequest or an error string explaining
   *         why it wasn't possible to create a function.
   */
  override def onBehalfOf(principal: Authentication.Principal): Either[String, WSRequest => WSRequest] = ???
}

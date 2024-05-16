package auth

import com.gu.mediaservice.lib.config.{CommonConfig, GridConfigResources}
import play.api.mvc.RequestHeader

class AuthConfig(resources: GridConfigResources) extends CommonConfig(resources) {
  val rootUri: RequestHeader => String = services.authBaseUri
  val mediaApiUri: RequestHeader => String = services.apiBaseUri
}

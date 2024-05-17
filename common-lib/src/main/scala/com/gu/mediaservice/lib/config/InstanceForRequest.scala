package com.gu.mediaservice.lib.config

import play.api.mvc.RequestHeader

trait InstanceForRequest {

  def instanceOf(request: RequestHeader): String = {
    // TODO some sort of filter supplied attribute
    request.host.split("\\.").head
  }

}

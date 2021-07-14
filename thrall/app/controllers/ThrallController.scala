package controllers

import play.api.mvc.{BaseController, ControllerComponents}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

class ThrallController(override val controllerComponents: ControllerComponents)(implicit val ec: ExecutionContext) extends BaseController {

  private implicit val ctx: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

  def index = Action {
    Ok("This is a thrall.")
  }

}

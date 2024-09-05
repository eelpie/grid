package lib

import com.gu.mediaservice.lib.ImageId
import com.gu.mediaservice.lib.aws.SqsViaSnsMessageConsumer
import com.gu.mediaservice.lib.logging.GridLogging
import com.gu.mediaservice.model.Instance
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MetadataSqsMessageConsumer(config: EditsConfig, metadataEditorMetrics: MetadataEditorMetrics, store: EditsStore) extends SqsViaSnsMessageConsumer(
  config.queueUrl, config, metadataEditorMetrics.snsMessage) with ImageId with GridLogging {

  override def chooseProcessor(subject: String): Option[JsValue => Future[Unit]] =
    PartialFunction.condOpt(subject) {
      case "image-deleted" => processDeletedImage
    }

  private def processDeletedImage(message: JsValue): Future[Unit] = {
    def deleteInstanceImage(id: String, instance: String) = {
      Future.delegate {
        implicit val i: Instance = Instance(id = instance)
        store.deleteItem(id)
      }
    }

    withImageIdAndInstance(message)(deleteInstanceImage)
  }
}

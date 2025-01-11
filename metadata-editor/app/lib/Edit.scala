package lib

import com.gu.mediaservice.lib.aws.UpdateMessage
import com.gu.mediaservice.model.{Edits, Instance}
import com.gu.mediaservice.syntax.MessageSubjects
import play.api.libs.json.JsObject

trait Edit extends MessageSubjects {

  def config: EditsConfig
  def editsStore: EditsStore
  def notifications: Notifications

  def publish(id: String, subject: String, instance: Instance)(metadata: JsObject): Edits = {
    val edits = metadata.as[Edits]
    val updateMessage = UpdateMessage(subject = subject, id = Some(id), edits = Some(edits), instance = instance.id)
    notifications.publish(updateMessage)
    edits
  }

}


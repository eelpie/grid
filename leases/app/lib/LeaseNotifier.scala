package lib

import com.gu.mediaservice.lib.aws.{ThrallMessageSender, UpdateMessage}
import com.gu.mediaservice.model.leases.MediaLease
import com.gu.mediaservice.syntax.MessageSubjects
import org.joda.time.DateTime

class LeaseNotifier(config: LeasesConfig, store: LeaseStore) extends ThrallMessageSender(config.thrallKinesisStreamConfig) with MessageSubjects {
  def sendReindexLeases(mediaId: String, instance: String) = {
    val leases = store.getForMedia(mediaId)
    val updateMessage = UpdateMessage(subject = ReplaceImageLeases, leases = Some(leases), id = Some(mediaId), instance = instance)
    publish(updateMessage)
  }

  def sendAddLease(mediaLease: MediaLease, instance: String) = {
    val updateMessage = UpdateMessage(subject = AddImageLease, mediaLease = Some(mediaLease), id = Some(mediaLease.mediaId), instance = instance)
    publish(updateMessage)
  }

  def sendAddLeases(mediaLeases: List[MediaLease], mediaId: String, instance: String) = {
    val updateMessage = UpdateMessage(subject = ReplaceImageLeases, leases = Some(mediaLeases), id = Some(mediaId), instance = instance)
    publish(updateMessage)
  }

  def sendRemoveLease(mediaId: String, leaseId: String, instance: String) = {
    val updateMessage = UpdateMessage(subject = RemoveImageLease, id = Some(mediaId), leaseId = Some(leaseId), instance = instance)
    publish(updateMessage)
  }
}

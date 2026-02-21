package model

import com.gu.mediaservice.model.usage.DigitalUsageMetadata
import org.joda.time.DateTime
import play.api.libs.json.{JodaReads, JodaWrites, Json, Reads, Writes}

case class DigitalMediaUsageRequest(digitalMediaUsageRecords: List[DigitalMediaUsageRecord])

object DigitalMediaUsageRequest {
  implicit val reads: Reads[DigitalMediaUsageRequest] = Json.reads[DigitalMediaUsageRequest]
  implicit val writes: Writes[DigitalMediaUsageRequest] = Json.writes[DigitalMediaUsageRequest]
}

case class DigitalMediaUsageRecord(
                                    dateAdded: DateTime,
                                    mediaId: String,
                                    metadata: DigitalUsageMetadata,
                                  )

object DigitalMediaUsageRecord {
  import JodaWrites._
  import JodaReads._

  implicit val reads: Reads[DigitalMediaUsageRecord] = Json.reads[DigitalMediaUsageRecord]
  implicit val writes: Writes[DigitalMediaUsageRecord] = Json.writes[DigitalMediaUsageRecord]
}

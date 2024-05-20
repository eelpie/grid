package lib

import model.StatusType.{Prepared, Queued}
import model.{UploadStatus, UploadStatusRecord}
import org.scanamo._
import org.scanamo.generic.auto._
import org.scanamo.syntax._

import scala.concurrent.{ExecutionContext, Future}
class UploadStatusTable(config: ImageLoaderConfig) {

  private val client = config.dynamoDBAsyncV2Builder().build()

  private val uploadStatusTable = Table[UploadStatusRecord](config.uploadStatusTable)

  def getStatus(imageId: String)(implicit ec: ExecutionContext) = {
    ScanamoAsync(client).exec(uploadStatusTable.get("id" === imageId))
  }

  def setStatus(uploadStatus: UploadStatusRecord)(implicit ec: ExecutionContext) = {
    ScanamoAsync(client).exec(uploadStatusTable.put(uploadStatus))
  }

  def updateStatus(imageId: String, updateRequest: UploadStatus)(implicit ec: ExecutionContext) = {
    val updateExpression = updateRequest.errorMessage match {
      case Some(error) => set("status", updateRequest.status) and set("errorMessages", error)
      case None => set("status", updateRequest.status)
    }
    val uploadStatusTableWithCondition =
      if(updateRequest.status == Queued) // can only transition to Queued status from Prepared status
        uploadStatusTable.when(attributeExists("id") and {"status" === Prepared.toString})
      else
        uploadStatusTable.when(attributeExists("id"))

    ScanamoAsync(client).exec(
      uploadStatusTableWithCondition.update(
          "id" -> imageId,
          update = updateExpression
        )
    )
  }

  def queryByUser(user: String)(implicit ec: ExecutionContext): Future[List[UploadStatusRecord]] = {
    ScanamoAsync(client).exec(uploadStatusTable.scan()).map {
      case Nil => List.empty[UploadStatusRecord]
      case recordsAndErrors => {
        recordsAndErrors
          .filter(item => item.isRight)
          .map(item => item.getOrElse(null))
          .filter(item => item.uploadedBy == user)
      }
    }
  }
}

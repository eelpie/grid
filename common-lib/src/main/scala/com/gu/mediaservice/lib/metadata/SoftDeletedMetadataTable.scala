package com.gu.mediaservice.lib.metadata

import com.amazonaws.services.dynamodbv2.model.BatchWriteItemResult
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.model.ImageStatusRecord
import org.scanamo.DeleteReturn.Nothing
import org.scanamo._
import org.scanamo.generic.auto._
import org.scanamo.syntax._

import scala.concurrent.{ExecutionContext, Future}

class SoftDeletedMetadataTable(config: CommonConfig) {

  private val client = config.dynamoDBAsyncV2Builder().build()

  private val softDeletedMetadataTable = Table[ImageStatusRecord](config.softDeletedMetadataTable)

  def getStatus(imageId: String)(implicit ex: ExecutionContext) = {
    ScanamoAsync(client).exec(softDeletedMetadataTable.get("id" === imageId))
  }

  def setStatus(imageStatus: ImageStatusRecord)(implicit ex: ExecutionContext) = {
    ScanamoAsync(client).exec(softDeletedMetadataTable.put(imageStatus))


  }

  private def extractUnprocessedIds(results: List[BatchWriteItemResult]): List[String] = {
    // TODO restore results.flatMap(_.getUnprocessedItems.values().asScala.flatMap(_.asScala.map(_.getPutRequest.getItem.get("id").getS)))
    List.empty
  }

  def setStatuses(imageStatuses: Set[ImageStatusRecord])(implicit ex: ExecutionContext) = {
    if (imageStatuses.isEmpty) Future.successful(List.empty)
    else ScanamoAsync(client).exec(softDeletedMetadataTable.putAll(imageStatuses)).map( r =>

 //     extractUnprocessedIds
      Seq.empty
    )
  }

  def clearStatuses(imageIds: Set[String])(implicit ex: ExecutionContext) = {
    //if (imageIds.isEmpty) Future.successful(List.empty)
    //else ScanamoAsync(client).exec(softDeletedMetadataTable.deleteAll("id" in imageIds)).map(extractUnprocessedIds)
    Future.successful(Seq.empty[String])
  }

  def updateStatus(imageId: String, isDeleted: Boolean)(implicit ex: ExecutionContext) = {
    /*
    val updateExpression = set('isDeleted -> isDeleted)
    ScanamoAsync(client).exec(
      softDeletedMetadataTable
        .given(attributeExists('id))
        .update(
          'id -> imageId,
          update = updateExpression
        )
    )

     */
    Future.successful(Nothing)
  }
}

package lib

import com.gu.mediaservice.lib.config.{CommonConfig, GridConfigResources}
import play.api.mvc.RequestHeader


class EditsConfig(resources: GridConfigResources) extends CommonConfig(resources) {
  val editsTable = string("dynamo.table.edits")
  val editsTablePhotoshootIndex = string("dynamo.globalsecondaryindex.edits.photoshoots")
  val syndicationTable = string("dynamo.table.syndication")

  val queueUrl = string("indexed.images.sqs.queue.url")

  val rootUri: RequestHeader => String = services.metadataBaseUri
}

package lib

import com.gu.mediaservice.lib.config.{CommonConfig, GridConfigResources}
import play.api.mvc.RequestHeader


class CollectionsConfig(resources: GridConfigResources) extends CommonConfig(resources) {
  val collectionsTable = string("dynamo.table.collections")
  val imageCollectionsTable = string("dynamo.table.imageCollections")

  val rootUri: RequestHeader => String = services.collectionsBaseUri
}

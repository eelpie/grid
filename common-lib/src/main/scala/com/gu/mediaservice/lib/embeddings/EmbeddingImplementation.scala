package com.gu.mediaservice.lib.embeddings

import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.ImageMetadata

import scala.concurrent.{ExecutionContext, Future}

trait EmbeddingImplementation {
  def createImageEmbeddings(source: Array[Byte], maybeMetadata: Option[ImageMetadata])(implicit ec: ExecutionContext, logMarker: LogMarker): Future[List[Float]]
  def createTextEmbedding(query: String)(implicit ec: ExecutionContext, logMarker: LogMarker): Future[List[Float]]
}

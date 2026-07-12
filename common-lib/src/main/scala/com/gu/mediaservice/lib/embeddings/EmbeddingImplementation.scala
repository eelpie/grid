package com.gu.mediaservice.lib.embeddings

import com.gu.mediaservice.lib.aws.EmbeddingSourceImageFormat
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.{Embedding, ImageMetadata, MimeType}

import scala.concurrent.{ExecutionContext, Future}

trait EmbeddingImplementation {
  def createImageEmbeddings(source: Array[Byte], mimeType: MimeType, maybeMetadata: Option[ImageMetadata])(implicit ec: ExecutionContext, logMarker: LogMarker): Future[Embedding]
  def createTextEmbedding(query: String)(implicit ec: ExecutionContext, logMarker: LogMarker): Future[List[Float]]
  def embeddingSourceImageFormat(): EmbeddingSourceImageFormat
}

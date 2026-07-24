package com.gu.mediaservice.lib.embeddings

import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.{Embedding, ImageMetadata, Jpeg, MimeType}

import scala.concurrent.{ExecutionContext, Future}

trait EmbeddingImplementation {
  def createImageEmbeddings(source: Array[Byte], mimeType: MimeType, maybeMetadata: Option[ImageMetadata])(implicit ec: ExecutionContext, logMarker: LogMarker): Future[Embedding]
  def createTextEmbedding(query: String)(implicit ec: ExecutionContext, logMarker: LogMarker): Future[List[Double]]
  def embeddingSourceImageFormat(): EmbeddingSourceImageFormat
}

case class EmbeddingSourceImageFormat(longestAxis: Int, format: MimeType = Jpeg, letterBox: Boolean)

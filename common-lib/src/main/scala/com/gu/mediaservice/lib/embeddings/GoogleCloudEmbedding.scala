package com.gu.mediaservice.lib.embeddings

import com.google.genai.Client
import com.google.genai.types._
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.{Embedding, GeminiEmbedding2, ImageMetadata}

import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._


class GoogleCloudEmbedding(projectId: String, location: String) {
  private val client = Client.builder().vertexAI(true).project(projectId).location(location).build()

  private val modelId = "gemini-embedding-2"

  private val embedContentConfig = EmbedContentConfig.builder()
    .outputDimensionality(768)
    .build()

  def createImageEmbeddings(source: Array[Byte], maybeMetadata: Option[ImageMetadata])(implicit ec: ExecutionContext, logMarker: LogMarker): Future[Embedding] = {
    Future {
      val imagePart = Some(Part.fromBytes(source, "image/png"))
      val titlePart = maybeMetadata.flatMap(_.title.map(Part.fromText))
      val descriptionPart = maybeMetadata.flatMap(_.description.map(Part.fromText))

      val parts = List(imagePart, titlePart, descriptionPart).flatten.asJava

      val content = Content.builder().
        parts(parts).
        build()

      val response = client.models.embedContent(modelId, content, embedContentConfig)

      val embeddings = firstEmbeddingFromResponse(response)
      Embedding(geminiEmbedding2 = Some(GeminiEmbedding2(embeddings.map(_.toDouble))))
    }
  }

  def createTextEmbedding(query: String)(implicit ec: ExecutionContext): Future[List[Double]] = {
    Future {
      val q = query
      val response = client.models.embedContent(modelId, q, embedContentConfig)
      firstEmbeddingFromResponse(response)
    }
  }

  private def firstEmbeddingFromResponse(response: EmbedContentResponse): List[Double] = {
    val a: Seq[ContentEmbedding] = response.embeddings().asScala.map(_.asScala.toSeq).getOrElse(Seq.empty)
    val v = a.head.values().asScala.map(_.asScala).getOrElse(Seq.empty).toSeq
    v.map(_.doubleValue()).toList
  }

}

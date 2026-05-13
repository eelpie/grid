package com.gu.mediaservice.lib.embeddings

import com.google.genai.Client
import com.google.genai.types._
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.{Embedding, GeminiEmbedding2}

import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.CollectionHasAsScala

class GoogleCloudEmbedding {

  private val projectId = "eelpie-cloud-registry"
  private val client = Client.builder().vertexAI(true).project(projectId).location("eu").build()

  private val modelId = "gemini-embedding-2"

  private val embedContentConfig = EmbedContentConfig.builder()
    .outputDimensionality(768)
    .build()

  def createImageEmbeddings(source: Array[Byte])(implicit ec: ExecutionContext, logMarker: LogMarker): Future[Embedding] = {
    Future {
      val p: Part = Part.fromBytes(source, "image/png")

      val content: Content = Content.builder().
        parts(p).
        build()

      val response = client.models.embedContent(modelId, content, embedContentConfig)

      val embeddings = firstEmbeddingFromResponse(response)
      Embedding(geminiEmbedding2 = Some(GeminiEmbedding2(embeddings.map(_.toDouble))))
    }
  }

  def createTextEmbedding(query: String)(implicit ec: ExecutionContext): Future[List[Double]] = {
    Future {
      val q = "task: search result | query: " +  query
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

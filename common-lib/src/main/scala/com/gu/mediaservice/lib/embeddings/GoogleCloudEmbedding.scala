package com.gu.mediaservice.lib.embeddings

import com.google.genai.Client
import com.google.genai.types.{Content, ContentEmbedding, EmbedContentConfig, Part}

import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.CollectionHasAsScala


class GoogleCloudEmbedding {

  private val projectId = "eelpie-cloud-registry"
  private val client = Client.builder().vertexAI(true).project(projectId).location("eu").build()
  private val models = client.models

  private val modelId = "gemini-embedding-2"

  private val embedContentConfig = EmbedContentConfig.builder()
    .outputDimensionality(1024)
    .build()


  def getImageEmbeddings(source: Array[Byte]): Seq[Float] = {
    val p: Part = Part.fromBytes(source, "image/jpeg")

    val content: Content = Content.builder().
      parts(p).
      build()

    val response = models.embedContent(modelId, content, embedContentConfig)

    val a: Seq[ContentEmbedding] = response.embeddings().asScala.map(_.asScala.toSeq).getOrElse(Seq.empty)
    val v = a.head.values().asScala.map(_.asScala).getOrElse(Seq.empty).toSeq
    v.map(_.floatValue())
  }

   def createTextEmbedding(query: String)(implicit ec: ExecutionContext): Future[List[Float]] = {
     Future {
       val response = models.embedContent(modelId, query, embedContentConfig)
       val a: Seq[ContentEmbedding] = response.embeddings().asScala.map(_.asScala.toSeq).getOrElse(Seq.empty)
       val v = a.head.values().asScala.map(_.asScala).getOrElse(Seq.empty).toSeq
       v.map(_.floatValue()).toList
     }
  }

}

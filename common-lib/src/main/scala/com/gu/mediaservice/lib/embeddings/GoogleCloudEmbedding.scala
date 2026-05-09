package com.gu.mediaservice.lib.embeddings

import com.google.genai.types.{Content, ContentEmbedding, EmbedContentConfig, Part}
import com.google.genai.{Client, Models}

import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.jdk.CollectionConverters.CollectionHasAsScala


class GoogleCloudEmbedding {

  def getImageEmbeddings(source: Array[Byte]): Seq[Float] = {
    val projectId = "eelpie-cloud-registry"

    val client = Client.builder().vertexAI(true).project(projectId).location("eu").build()

    val p: Part = Part.fromBytes(source, "image/jpeg")

    val content: Content = Content.builder().
      parts(p).
      build()

    val config = EmbedContentConfig.builder()
      .outputDimensionality(1024)
      .build()

    val models: Models = client.models
    val response = models.embedContent("gemini-embedding-2", content, config)


    val a: Seq[ContentEmbedding] = response.embeddings().asScala.map(_.asScala.toSeq).getOrElse(Seq.empty)
    val v = a.head.values().asScala.map(_.asScala).getOrElse(Seq.empty).toSeq
    v.map(_.floatValue())
  }

}

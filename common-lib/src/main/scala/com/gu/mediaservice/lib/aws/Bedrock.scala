package com.gu.mediaservice.lib.aws

import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.embeddings.{EmbeddingImplementation, EmbeddingSourceImageFormat}
import com.gu.mediaservice.lib.logging.LogMarker
import com.gu.mediaservice.model.{CohereV4Embedding, Embedding, ImageMetadata, Jpeg, MimeType}
import org.apache.commons.codec.binary.Base64
import play.api.libs.json.OFormat.oFormatFromReadsAndOWrites
import play.api.libs.json._
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.bedrockruntime._
import software.amazon.awssdk.services.bedrockruntime.model._

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

object Bedrock {
  private case class BedrockTextRequest(
    input_type: String,
    embedding_types: List[String],
    texts: List[String],
    output_dimension: Int
  )

  private implicit val bedrockTextRequestFormat: OFormat[BedrockTextRequest] = Json.format[BedrockTextRequest]

  case class BedrockImageRequest(
    input_type: String,
    embedding_types: List[String],
    images: List[String],
    output_dimension: Int
  )

  private implicit val bedrockImageRequestFormat: OFormat[BedrockImageRequest] = Json.format[BedrockImageRequest]
}

class Bedrock(config: CommonConfig)
  extends EmbeddingImplementation with AwsClientBuilderUtils {

  // TODO: figure out what the more usual pattern for turning off localstack behaviour is
  override def awsLocalEndpointUri: Option[URI] = None

  override def isDev: Boolean = config.isDev

  val client: BedrockRuntimeClient = {
    withAWSCredentials(BedrockRuntimeClient.builder())
      .build()
  }

  private def createSearchQueryRequestBody(inputData: String): InvokeModelRequest = {
    val body = Bedrock.BedrockTextRequest(
      input_type = "search_query",
      embedding_types = List("float"),
      texts = List(inputData),
      output_dimension = 256
    )
    val jsonBody = Json.toJson(body).toString()

    val request: InvokeModelRequest = {
      InvokeModelRequest
        .builder()
        .accept("*/*")
        .body(SdkBytes.fromUtf8String(jsonBody))
        .contentType("application/json")
        .modelId("global.cohere.embed-v4:0")
        .build()
    }
    request
  }

  private def createImageSearchDocumentRequestBody(base64Image: String, imageMimeType: MimeType): InvokeModelRequest = {
    val body = Bedrock.BedrockImageRequest(
      input_type = "search_document",
      embedding_types = List("float"),
      images = List(
        s"`data:${imageMimeType.name};base64,$base64Image`"
      ),
      output_dimension = 1536
    )

    val jsonBody = Json.toJson(body).toString()

    val request: InvokeModelRequest = {
      InvokeModelRequest
        .builder()
        .accept("*/*")
        .body(SdkBytes.fromUtf8String(jsonBody))
        .contentType("application/json")
        .modelId("global.cohere.embed-v4:0")
        .build()
    }
    request
  }

  private def sendBedrockEmbeddingRequest(requestBody: InvokeModelRequest)(
    implicit logMarker: LogMarker
  ): InvokeModelResponse = {
    try {
      val response = client.invokeModel(requestBody)
      logger.info(
        logMarker,
        s"Bedrock API call to create image embedding completed with status: ${response.sdkHttpResponse().statusCode()}"
      )
      response
    }
    catch {
      case e: Exception =>
        logger.error(logMarker, "Exception during Bedrock API call to create image embedding", e)
        throw e
    }
  }

  def createTextEmbedding(inputData: String)(implicit ec: ExecutionContext, logMarker: LogMarker): Future[List[Double]] = {
    val requestBody = createSearchQueryRequestBody(inputData)
    val bedrockFuture = Future { sendBedrockEmbeddingRequest(requestBody) }
    bedrockFuture.map { response =>
      val responseBody = response.body().asUtf8String()
      val json = Json.parse(responseBody)
      // Extract the embedding array (first element since it's an array of arrays)
      val embedding = (json \ "embeddings" \ "float")(0).as[List[Double]]
      logger.info(
        logMarker,
        s"Successfully extracted text embedding. Vector size: ${embedding.size}"
      )
      embedding
    }
  }

  override def createImageEmbeddings(source: Array[Byte], mimeType: MimeType, maybeMetadata: Option[ImageMetadata])(implicit ec: ExecutionContext, logMarker: LogMarker): Future[Embedding] = {
    val base64ImageData = Base64.encodeBase64String(source)
    val requestBody = createImageSearchDocumentRequestBody(
      base64ImageData, embeddingSourceImageFormat().format
    )
    val bedrockFuture = Future {
      sendBedrockEmbeddingRequest(requestBody)
    }
    bedrockFuture.map { response =>
      val responseBody = response.body().asUtf8String()
      val json = Json.parse(responseBody)
      // Extract the embedding array (first element since it's an array of arrays)
      val embeddings = (json \ "embeddings" \ "float")(0).as[List[Double]]
      logger.info(
        logMarker,
        s"Successfully created image embedding. Vector size: ${embeddings.size}"
      )
      embeddings
    }.map { embeddings =>
      Embedding(
        cohereEmbedV4 = Some(CohereV4Embedding(embeddings))
      )
    }
  }

  override def embeddingSourceImageFormat(): EmbeddingSourceImageFormat = EmbeddingSourceImageFormat(longestAxis = 3000, format = Jpeg, letterBox = false)

}

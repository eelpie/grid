package lib.kinesis

import com.gu.mediaservice.model.Instance
import lib.elasticsearch.ElasticSearchTestBase
import org.scalatestplus.mockito.MockitoSugar

import java.util.UUID

class ThrallEventConsumerTest extends ElasticSearchTestBase with MockitoSugar {

  override def instance: Instance = Instance(UUID.randomUUID().toString)

  "parse message" - {
    "parse minimal message" in {
      val j =
        """
          |{
          | "subject":"delete-image",
          | "id":"123",
          | "lastModified":"2021-01-25T10:21:18.006Z",
          | "instance":"an-instance"
          |}
          |""".stripMargin.getBytes()
      val m2 = ThrallEventConsumer.parseRecord(j, java.time.Instant.EPOCH)
      m2.isRight shouldEqual (true)
      m2.right.get.subject shouldBe "DeleteImageMessage"
    }
  }
}


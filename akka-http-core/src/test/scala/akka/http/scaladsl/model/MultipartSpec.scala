/*
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.http.scaladsl.model

import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.{ BeforeAndAfterAll, Inside, Matchers, WordSpec }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import akka.actor.ActorSystem
import akka.testkit._
import headers._

class MultipartSpec extends WordSpec with Matchers with Inside with BeforeAndAfterAll {

  val testConf: Config = ConfigFactory.parseString("""
  akka.event-handlers = ["akka.testkit.TestEventListener"]
  akka.loglevel = WARNING""")
  implicit val system = ActorSystem(getClass.getSimpleName, testConf)
  implicit val materializer = ActorMaterializer()
  override def afterAll() = TestKit.shutdownActorSystem(system)

  "Multipart.General" should {
    "support `toStrict` on the streamed model" in {
      val streamed = Multipart.General(
        MediaTypes.`multipart/mixed`,
        Source(Multipart.General.BodyPart(defaultEntity("data"), List(ETag("xzy"))) :: Nil))
      val strict = Await.result(streamed.toStrict(1.second.dilated), 1.second.dilated)

      strict shouldEqual Multipart.General(
        MediaTypes.`multipart/mixed`,
        Multipart.General.BodyPart.Strict(HttpEntity("data"), List(ETag("xzy"))))
    }

    "support `toEntity`" in {
      val streamed = Multipart.General(
        MediaTypes.`multipart/mixed`,
        Source(Multipart.General.BodyPart(defaultEntity("data"), List(ETag("xzy"))) :: Nil))
      val result = streamed.toEntity("boundary")
      result.contentType shouldBe MediaTypes.`multipart/mixed`.withBoundary("boundary").toContentType
      val encoding = Await.result(result.dataBytes.runWith(Sink.seq), 1.second.dilated)
      encoding.map(_.utf8String).mkString shouldBe "--boundary\r\nContent-Type: text/plain; charset=UTF-8\r\nETag: \"xzy\"\r\n\r\ndata\r\n--boundary--"
    }
  }

  "Multipart.FormData" should {
    "support `toStrict` on the streamed model" in {
      val streamed = Multipart.FormData(Source(
        Multipart.FormData.BodyPart("foo", defaultEntity("FOO")) ::
          Multipart.FormData.BodyPart("bar", defaultEntity("BAR")) :: Nil))
      val strict = Await.result(streamed.toStrict(1.second.dilated), 1.second.dilated)

      strict shouldEqual Multipart.FormData(Map("foo" → HttpEntity("FOO"), "bar" → HttpEntity("BAR")))
    }
  }

  "Multipart.ByteRanges" should {
    "support `toStrict` on the streamed model" in {
      val streamed = Multipart.ByteRanges(Source(
        Multipart.ByteRanges.BodyPart(ContentRange(0, 6), defaultEntity("snippet"), _additionalHeaders = List(ETag("abc"))) ::
          Multipart.ByteRanges.BodyPart(ContentRange(8, 9), defaultEntity("PR"), _additionalHeaders = List(ETag("xzy"))) :: Nil))
      val strict = Await.result(streamed.toStrict(1.second.dilated), 1.second.dilated)

      strict shouldEqual Multipart.ByteRanges(
        Multipart.ByteRanges.BodyPart.Strict(ContentRange(0, 6), HttpEntity("snippet"), additionalHeaders = List(ETag("abc"))),
        Multipart.ByteRanges.BodyPart.Strict(ContentRange(8, 9), HttpEntity("PR"), additionalHeaders = List(ETag("xzy"))))
    }
  }

  def defaultEntity(content: String) =
    HttpEntity.Default(ContentTypes.`text/plain(UTF-8)`, content.length, Source(ByteString(content) :: Nil))
}

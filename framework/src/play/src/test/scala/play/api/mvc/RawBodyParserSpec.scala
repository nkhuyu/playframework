/*
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package play.api.mvc

import akka.util.ByteString
import akka.stream.scaladsl.Source
import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer

import java.io.IOException

import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import play.api.http.ParserConfiguration
import play.core.test.FakeRequest

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object RawBodyParserSpec extends Specification with AfterAll {

  val system = ActorSystem("content-types-spec")
  implicit val materializer = ActorFlowMaterializer()(system)

  def afterAll(): Unit = system.shutdown()

  val config = ParserConfiguration()

  def parse(body: ByteString, memoryThreshold: Int = config.maxMemoryBuffer, maxLength: Long = config.maxDiskBuffer): Either[Result, RawBuffer] = {
    val request = FakeRequest(method = "GET", "/x")
    val bodyParser = new BodyParsers {}
    val parser = bodyParser.parse.raw(memoryThreshold, maxLength)
    Await.result(parser(request).run(Source.single(body)), Duration.Inf)
  }

  "Raw Body Parser" should {
    "parse a simple body" in {
      val body = ByteString("lorem ipsum")
      parse(body) must beRight.like {
        case rawBuffer => rawBuffer.asBytes() must beSome.like {
          case outBytes =>
            outBytes mustEqual body
        }
      }
    }

    "close the raw buffer after parsing the body" in {
      val body = ByteString("lorem ipsum")
      parse(body, memoryThreshold = 1) must beRight.like {
        case rawBuffer =>
          rawBuffer.push(ByteString("This fails because the stream was closed!")) must throwA[IOException]
      }
    }

    "fail to parse longer than allowed body" in {
      val msg = ByteString("lorem ipsum")
      parse(msg, maxLength = 1) must beLeft
    }
  }
}
package com.github.pbyrne84.zioscalanativelambda.message

import com.github.pbyrne84.zioscalanativelambda.shared.BaseSpec
import com.github.pbyrne84.zioscalanativelambda.shared.BaseSpec.Shared
import io.circe.{Decoder, Json, ParsingFailure}
import zio.test._
import zio.{IO, Scope, ZIO}

import java.util.UUID

object SqsDecodingSpec extends BaseSpec {

  private val receiptHandle =
    """AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RIN=="""

  private val messageId = UUID.fromString("a78cdb8b-d8cb-4c28-be77-fc89608f022c")

  override def spec: Spec[Shared with TestEnvironment with Scope, Any] =
    suite("SqsDecoding")(
      suite("should decode a single message")(
        test("when the message body is a simply string") {
          val bodyString = "I, am, the, one, and, only"
          implicit val sqsMessageStrongDecoder: Decoder[SqsDecoding[String]] = SqsDecoding.decoder(Decoder.decodeString)

          for {
            parsedJson <- createMessageJson(s"\"$bodyString\"")
            result = io.circe.parser.decode[SqsDecoding[String]](parsedJson.spaces2)
          } yield assertTrue(result == Right(createSqsDecoding(bodyString)))
        },
        test("when the message body is encoded json of an object that can be a case class") {
          val exampleBody = ExampleBody(name = "Unknown")
          val encodedBody = "\"{\\n  \\\"name\\\" : \\\"Unknown\\\"\\n}\""

          implicit val sqsMessageStrongDecoder: Decoder[SqsDecoding[ExampleBody]] =
            ExampleBody.sqsEncodedMessageBodyStringDecoder

          for {
            jsonWithEncodedBody <- createMessageJson(encodedBody)
            result = io.circe.parser.decode[SqsDecoding[ExampleBody]](jsonWithEncodedBody.spaces2)
          } yield assertTrue(result == Right(createSqsDecoding(exampleBody)))
        }
      ),
      suite("decode many")(
        test("receiving a message from amazon") {
          val body1String = "body1"
          val body2String = "body2"

          for {
            body1Json <- createMessageJson(s"\"$body1String\"")
            body2Json <- createMessageJson(s"\"$body2String\"")
            amazonMessages =
              s"""
                 |{
                 |    "Records": [
                 |       $body1Json,
                 |       $body2Json
                 |    ]
                 |}
                 |""".stripMargin
            result = SqsDecoding.decodeMany(amazonMessages)(Decoder.decodeString)

          } yield assertTrue(
            result == Right(
              List(
                createSqsDecoding(body1String),
                createSqsDecoding(body2String)
              )
            )
          )

        }
      )
    )

  // return io so the test thingy has one io to make it happy
  private def createMessageJson(messageBody: String): IO[ParsingFailure, Json] = {
    ZIO.fromEither(
      parseJson(
        s"""
         |{
         |    "messageId": "$messageId",
         |    "receiptHandle": "$receiptHandle",
         |    "body": $messageBody,
         |    "attributes": {
         |        "ApproximateReceiveCount": "1",
         |        "SentTimestamp": "1670426083016"
         |    },
         |    "messageAttributes": {},
         |    "md5OfBody": "a390ff989d692670fa09d8d64b134179",
         |    "eventSource": "aws:sqs",
         |    "eventSourceARN": "arn:aws:sqs:eu-west-2:538645939706:test-queue",
         |    "awsRegion": "eu-west-2"
         |}
         |""".stripMargin
      )
    )
  }

  def createSqsDecoding[A](body: A): SqsDecoding[A] = {
    SqsDecoding[A](
      messageId = messageId,
      receiptHandle = receiptHandle,
      body = body,
      attributes = Map(
        "ApproximateReceiveCount" -> "1",
        "SentTimestamp" -> "1670426083016"
      ),
      messageAttributes = Map.empty,
      md5OfBody = "a390ff989d692670fa09d8d64b134179",
      eventSource = "aws:sqs",
      eventSourceARN = "arn:aws:sqs:eu-west-2:538645939706:test-queue",
      awsRegion = "eu-west-2"
    )
  }

}

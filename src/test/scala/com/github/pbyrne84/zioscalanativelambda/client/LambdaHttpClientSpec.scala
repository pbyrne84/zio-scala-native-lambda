package com.github.pbyrne84.zioscalanativelambda.client

import com.github.pbyrne84.zioscalanativelambda.message.SqsDecoding
import com.github.pbyrne84.zioscalanativelambda.shared.BaseSpec
import com.github.pbyrne84.zioscalanativelambda.shared.wiremock.LambdaWireMock
import io.circe.Decoder
import sttp.model.Header
import zio.test.TestAspect._
import zio.test._
import zio.test.Assertion._
import java.util.UUID

object LambdaHttpClientSpec extends BaseSpec {

  private val messageBody =
    """
      |{
      |  "Records": [
      |    {
      |      "messageId": "9d9868cb-2ea7-4e67-9661-7310ea6354f2",
      |      "receiptHandle": "AQEBlI+oiogEjaSs56uTUzJbk6tp4y6ai6==",
      |      "body": "I Like Food",
      |      "attributes": {
      |        "ApproximateReceiveCount": "7",
      |        "SentTimestamp": "1671539235847"
      |      },
      |      "messageAttributes": {},
      |      "md5OfBody": "886251e46ceb8fddeb5dc79b26b2fab1",
      |      "eventSource": "aws:sqs",
      |      "eventSourceARN": "arn:aws:sqs:eu-west-2:538645939706:zio-lambda-test-queue",
      |      "awsRegion": "eu-west-2"
      |    }
      |  ]
      |}
      |""".stripMargin

  private val expectedDecodedMessage: SqsDecoding[String] = SqsDecoding(
    messageId = UUID.fromString("9d9868cb-2ea7-4e67-9661-7310ea6354f2"),
    receiptHandle = "AQEBlI+oiogEjaSs56uTUzJbk6tp4y6ai6==",
    "I Like Food",
    attributes = Map(
      "ApproximateReceiveCount" -> "7",
      "SentTimestamp" -> "1671539235847"
    ),
    messageAttributes = Map.empty,
    md5OfBody = "886251e46ceb8fddeb5dc79b26b2fab1",
    eventSource = "aws:sqs",
    eventSourceARN = "arn:aws:sqs:eu-west-2:538645939706:zio-lambda-test-queue",
    awsRegion = "eu-west-2"
  )

  override def spec = {
    implicit val sqsMessageStrongDecoder: Decoder[String] = Decoder.decodeString

    suite("LambdaHttpClientSpec")(
      suite("getNextMessage")(
        test("should return response with request id from the header in a case insensitive fashion") {
          for {
            _ <- reset
            responseHeaders = List("Lambda-runtime-aws-request-Id" -> "header-request-id")
            _ <- LambdaWireMock.stubGetNextMessageCall(messageBody, responseHeaders)
            result <- LambdaHttpClient.getNextMessage
          } yield assertTrue(
            result == NextMessageResponse("header-request-id", List(expectedDecodedMessage))
          )
        },
        test("should fail if the request id header does not exist") {
          for {
            _ <- reset
            responseHeaders = List.empty
            _ <- LambdaWireMock.stubGetNextMessageCall(messageBody, responseHeaders)
            result <- LambdaHttpClient.getNextMessage.mapError(error => error.getClass).exit
          } yield assert(result)(fails(equalTo(classOf[NextMessageError])))
        }
      ),
      suite("sendInvocationResponse")(
        test("return response with headers") {
          for {
            _ <- reset
            requestId = "234545"
            _ <- LambdaWireMock.stubInvocationResponseCall(requestId, "another banana")
            result <- LambdaHttpClient.sendInvocationResponse(requestId, "xxxxxxx")
          } yield assertTrue(
            result.body == Right("another banana")
          )
        }
      )
    ).provideSome[BaseSpec.Shared](LambdaHttpClient.layer)

  } @@ sequential

}

package com.github.pbyrne84.zioscalanativelambda.shared

import com.github.pbyrne84.zioscalanativelambda.shared.wiremock.LambdaWireMock
import io.circe.{Json, ParsingFailure}
import zio.logging.backend.SLF4J
import zio.test.ZIOSpec
import zio.{ZIO, ZLayer}

object BaseSpec {

  // guarantee we are using this logger in all the tests
  val logger = zio.Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val layer = {
    ZLayer.make[
      InitialisedParams with EnvironmentParamSetup
    ](
      InitialisedParams.layer,
      EnvironmentParamSetup.layer,
      logger
    )
  }

  val layerWithWireMock: ZLayer[
    Any,
    Nothing,
    LambdaWireMock with InitialisedParams with EnvironmentParamSetup
  ] = {
    ZLayer.make[
      LambdaWireMock with InitialisedParams with EnvironmentParamSetup
    ](
      BaseSpec.layer,
      LambdaWireMock.layer,
      logger
    )
  }

  type Shared = LambdaWireMock with InitialisedParams with EnvironmentParamSetup
}

abstract class BaseSpec extends ZIOSpec[BaseSpec.Shared] {

  val bootstrap = {
    System.setProperty("AWS_LAMBDA_RUNTIME_API", "") // hackery but the test application conf should nullify this
    BaseSpec.layerWithWireMock
  }

  protected def reset: ZIO[BaseSpec.Shared, Throwable, Unit] = {
    for {
      _ <- LambdaWireMock.reset
    } yield ()
  }

  protected def getConfig: ZIO[EnvironmentParamSetup, Nothing, InitialisedParams] =
    InitialisedParams.getParams

  def parseJson(json: String): Either[ParsingFailure, Json] = {
    io.circe.parser.parse(json) match {
      case Left(error: ParsingFailure) =>
        Left(ParsingFailure(s"parsing $json failed with ${error.message}", error.underlying))
      case Right(parsedJson) => Right(parsedJson)
    }
  }

  // just for namespacing as bunging things in the parent gets nasty with the scopy getting overloaded
  // so you cannot find anything without opening the class up which gets annoying.
  // This way things can be moved out to there own class and then instances can be assigned to sqsMessage.
  object sqsMessage {
    def createValid(messageBody: String): String = {
      val messageAsJsonString = Json.fromString(messageBody).noSpaces
      s"""
            |{
            |  "Records": [
            |    {
            |      "messageId": "9d9868cb-2ea7-4e67-9661-7310ea6354f2",
            |      "receiptHandle": "AQEBlI+oiogEjaSs56uTUzJbk6tp4y6ai6==",
            |      "body": $messageAsJsonString,
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
    }

    def createExpectedInvocationMessage( text: String): Either[ParsingFailure, Json] = {
      parseJson(s"""
          |{
          |  "status" : "$text"
          |}
          |""".stripMargin)
    }
  }

}

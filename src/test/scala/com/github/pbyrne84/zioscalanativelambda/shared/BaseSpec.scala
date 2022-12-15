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

}

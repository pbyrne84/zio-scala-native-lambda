package com.github.pbyrne84.zioscalanativelambda

import com.github.pbyrne84.zioscalanativelambda.client.LambdaHttpClient
import com.github.pbyrne84.zioscalanativelambda.message.SqsDecoding
import io.circe.Decoder
import zio.logging.LogAnnotation
import zio.logging.backend.SLF4J
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

import java.util.UUID

object LambdaMain extends ZIOAppDefault {
  private val loggingLayer = zio.Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val stringTraceId: LogAnnotation[String] = LogAnnotation[String](
    name = "trace_id",
    combine = (_: String, r: String) => r,
    render = _.toString
  )

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {

    implicit val sqsMessageStrongDecoder: Decoder[String] = Decoder.decodeString

    (for {
      nextMessage <- LambdaHttpClient.getNextMessage
      _ <- ZIO.log(s"response body of message ${nextMessage}")
      _ <- ZIO.log(s"deleting request id ${nextMessage.headerRequestId}")
      sendResponse <- LambdaHttpClient.sendInvocationResponse(nextMessage.headerRequestId, "banana")
      _ <- ZIO.log(s"send body of message ${sendResponse.body}")
      _ <- ZIO.log(s"send headers of message ${sendResponse.headers}")
    } yield ())
      .tapError((error: Throwable) => ZIO.fail(error))
      .logError("failed processing message")

  }.provide(LambdaHttpClient.layer, loggingLayer) @@ stringTraceId(UUID.randomUUID().toString)
}

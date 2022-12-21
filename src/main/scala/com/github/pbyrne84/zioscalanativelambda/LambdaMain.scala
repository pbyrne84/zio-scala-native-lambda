package com.github.pbyrne84.zioscalanativelambda

import com.github.pbyrne84.zioscalanativelambda.client.{LambdaHttpClient, NextMessageError, NextMessageResponse}
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
    LamdaSqsService.processSqsMessages
  }.provide(LambdaHttpClient.layer, loggingLayer, LamdaSqsService.layer) @@ stringTraceId(UUID.randomUUID().toString)
}

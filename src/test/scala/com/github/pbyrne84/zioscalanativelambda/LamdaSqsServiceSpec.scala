package com.github.pbyrne84.zioscalanativelambda

import com.github.pbyrne84.zioscalanativelambda.client.{LambdaHttpClient, NextMessageError}
import com.github.pbyrne84.zioscalanativelambda.shared.BaseSpec
import com.github.pbyrne84.zioscalanativelambda.shared.BaseSpec.Shared
import com.github.pbyrne84.zioscalanativelambda.shared.wiremock.LambdaWireMock
import zio.test.TestAspect.sequential
import zio.{Scope, ZIO}
import zio.test._
import zio.test.Assertion._

object LamdaSqsServiceSpec extends BaseSpec {

  private val headerRequestId = "header-request-id"
  private val validLambdaResponseHeaders = List("Lambda-runtime-aws-request-Id" -> headerRequestId)

  override def spec: Spec[Shared with TestEnvironment with Scope, Any] = {
    suite("processSqsMessages")(
      test("should mark the message as processed if the message can be decoded properly") {
        val sqsMessageJson = sqsMessage.createValid("I Like More Food")

        for {
          _ <- reset
          expectedInvocationResponse <- ZIO.fromEither(sqsMessage.createExpectedInvocationMessage("SUCCESS"))
          _ <- LambdaWireMock.stubGetNextMessageCall(sqsMessageJson, validLambdaResponseHeaders)
          _ <- LambdaWireMock.stubInvocationResponseCall(headerRequestId, expectedInvocationResponse.spaces2)
          _ <- LamdaSqsService.processSqsMessages
          // without verification how can we be sure anything happened ?
          _ <- LambdaWireMock.verifyInvocationResponseCall(headerRequestId, expectedInvocationResponse.spaces2)
          unexpectedCalls <- LambdaWireMock.getUnexpectedCalls
        } yield assertTrue(unexpectedCalls == List.empty)
      },
      test(
        "should mark the message as processed if the message cannot be decoded properly returning the failure after"
      ) {
        for {
          _ <- reset
          expectedInvocationResponse <- ZIO.fromEither(sqsMessage.createExpectedInvocationMessage("FAILED"))
          _ <- LambdaWireMock.stubGetNextMessageCall("{}", validLambdaResponseHeaders)
          _ <- LambdaWireMock.stubInvocationResponseCall(headerRequestId, expectedInvocationResponse.spaces2)
          result <- LamdaSqsService.processSqsMessages.mapError(error => error.getClass).exit
          // without verification how can we be sure anything happened ?
          _ <- LambdaWireMock.verifyInvocationResponseCall(headerRequestId, expectedInvocationResponse.spaces2)
          unexpectedCalls <- LambdaWireMock.getUnexpectedCalls
        } yield assert(result)(fails(equalTo(classOf[NextMessageError]))) && assertTrue(unexpectedCalls == List.empty)
      },
      test(
        "should simply fail if message has no valid request id in the header"
      ) {
        for {
          _ <- reset
          headerRequestId = "header-request-id"
          responseHeaders = List("other-header" -> headerRequestId)
          _ <- LambdaWireMock.stubGetNextMessageCall("{}", responseHeaders)
          result <- LamdaSqsService.processSqsMessages.mapError(error => error.getClass).exit
          // without verification how can we be sure anything happened ?
          unexpectedCalls <- LambdaWireMock.getUnexpectedCalls
        } yield assert(result)(fails(equalTo(classOf[NextMessageError]))) && assertTrue(unexpectedCalls == List.empty)
      }
    )
  }.provideSome[BaseSpec.Shared](LambdaHttpClient.layer, LamdaSqsService.layer) @@ sequential
}

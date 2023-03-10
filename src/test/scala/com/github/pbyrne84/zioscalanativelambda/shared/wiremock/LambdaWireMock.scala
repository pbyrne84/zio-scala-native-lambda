package com.github.pbyrne84.zioscalanativelambda.shared.wiremock

import com.github.pbyrne84.zioscalanativelambda.shared.InitialisedParams
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.{HttpHeader, HttpHeaders}
import com.github.tomakehurst.wiremock.matching.{RequestPattern, RequestPatternBuilder}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import com.typesafe.scalalogging.StrictLogging
import zio.{Task, ZIO, ZLayer}

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

object LambdaWireMock {

  private val zioService = ZIO.serviceWithZIO[LambdaWireMock]

  val layer: ZLayer[InitialisedParams, Nothing, LambdaWireMock] = ZLayer {
    for {
      initialisedParams <- ZIO.service[InitialisedParams]
      amazonLambdaPort <- ZIO.succeed(new TestWireMock(initialisedParams.amazonLambdaPort))
    } yield new LambdaWireMock(amazonLambdaPort)
  }

  def stubGetNextMessageCall(
      response: String,
      responseHeaders: List[(String, String)]
  ): ZIO[LambdaWireMock, Throwable, Unit] =
    zioService(_.stubGetNextMessageCall(response, responseHeaders))

  def stubInvocationResponseCall(
      requestId: String,
      response: String
  ): ZIO[LambdaWireMock, Throwable, Unit] =
    zioService(_.stubInvocationResponseCall(requestId, response))

  def verifyInvocationResponseCall(
      requestId: String,
      invocationResponse: String
  ): ZIO[LambdaWireMock, Throwable, Unit] = {
    zioService(_.verifyInvocationResponseCall(requestId, invocationResponse))
  }

  def getStubbings: ZIO[LambdaWireMock, Throwable, List[StubMapping]] =
    zioService(_.getStubbings)

  def getUnexpectedCalls: ZIO[LambdaWireMock, Throwable, List[LoggedRequest]] =
    zioService(_.getUnexpectedCalls)

  def verifyHeaders(headers: List[(String, String)]): ZIO[LambdaWireMock, Throwable, Unit] = {
    zioService(_.verifyHeaders(headers))
  }

  def reset: ZIO[LambdaWireMock, Throwable, Unit] = zioService(_.reset)

}

class LambdaWireMock(testWireMock: TestWireMock) extends StrictLogging {
  import WireMock._
  // As this is part of a shared service layer this should only fire once.
  // Read the readme about why this may not be the case (object.main).
  logger.info(s"creating ${LambdaWireMock.getClass.getSimpleName}")

  private val getNextMessageUrl = s"/2018-06-01/runtime/invocation/next"
  private val invocationResponseUrlFormat = s"/2018-06-01/runtime/invocation/%s/response"

  def reset: Task[Unit] =
    testWireMock.reset

  def stubGetNextMessageCall(response: String, responseHeaders: List[(String, String)]): Task[Unit] = {
    stubCall(getNextMessageUrl, response, responseHeaders)
  }

  private def stubCall(url: String, response: String, responseHeaders: List[(String, String)]): Task[Unit] = {
    val convertedHeaders = new HttpHeaders(responseHeaders.map { case (name, value) =>
      new HttpHeader(name, value)
    }: _*)

    ZIO.attempt {
      testWireMock.wireMock.stubFor(
        WireMock
          .any(WireMock.urlMatching(url))
          .willReturn(aResponse().withBody(response).withHeaders(convertedHeaders))
      )
    }
  }

  def stubInvocationResponseCall(requestId: String, response: String): Task[Unit] = {
    stubCall(invocationResponseUrlFormat.format(requestId), response, List.empty)
  }

  def verifyInvocationResponseCall(requestId: String, invocationResponse: String): Task[Unit] = {
    ZIO.attempt {
      testWireMock.wireMock.verify(
        WireMock
          .postRequestedFor(WireMock.urlEqualTo(invocationResponseUrlFormat.format(requestId)))
          .withRequestBody(WireMock.equalToJson(invocationResponse))
      )
    }
  }

  def verifyHeaders(headers: List[(String, String)]): Task[Unit] = {
    // wiremock is a builder builder builder mutation thingy
    val builder: RequestPatternBuilder = anyRequestedFor(urlMatching(".*"))
    val verification = headers.foldLeft(builder) { case (request, (name, value)) =>
      request.withHeader(name, matching(value))
    }

    ZIO.attempt(testWireMock.wireMock.verify(verification))
  }

  def getStubbings: Task[List[StubMapping]] = {
    ZIO.attemptBlocking(testWireMock.wireMock.getStubMappings.asScala.toList)
  }

  def getUnexpectedCalls: Task[List[LoggedRequest]] = {
    ZIO.attemptBlocking(testWireMock.wireMock.findAllUnmatchedRequests().asScala.toList)
  }

}

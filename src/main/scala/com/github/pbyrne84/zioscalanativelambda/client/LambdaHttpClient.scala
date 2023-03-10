package com.github.pbyrne84.zioscalanativelambda.client

import com.github.pbyrne84.zioscalanativelambda.config.LambdaConfig
import com.github.pbyrne84.zioscalanativelambda.message.SqsDecoding
import io.circe.Decoder
import sttp.client3.{Request, Response}
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.config.ReadError
import zio.{IO, Task, ZIO, ZLayer}

case class NextMessageResponse[A](headerRequestId: String, messages: List[SqsDecoding[A]])
case class NextMessageError(maybeRequestId: Option[String], message: String, maybeCause: Option[Throwable])
    extends RuntimeException(message, maybeCause.orNull)

object LambdaHttpClient {
  val layer: ZLayer[Any, ReadError[String], LambdaHttpClient] = ZLayer {
    for {
      config <- ZIO.service[LambdaConfig].provide(LambdaConfig.layer)
    } yield new LambdaHttpClient(config)
  }

  def getNextMessage[A](implicit
      bodyDecoder: Decoder[A]
  ): ZIO[LambdaHttpClient, NextMessageError, NextMessageResponse[A]] = {
    ZIO.serviceWithZIO[LambdaHttpClient](_.getNextMessage)
  }

  def sendInvocationResponse(
      requestId: String,
      message: String
  ): ZIO[LambdaHttpClient, Throwable, Response[Either[String, String]]] = {
    ZIO.serviceWithZIO[LambdaHttpClient](_.sendInvocationResponse(requestId, message))
  }

}

//pulumi new aws-typescript eu-west-2
class LambdaHttpClient(lambdaConfig: LambdaConfig) {

  import sttp.client3._

  private val requestIdHeaderName = "lambda-runtime-aws-request-id"

  def getNextMessage[A](implicit bodyDecoder: Decoder[A]): ZIO[Any, NextMessageError, NextMessageResponse[A]] = {
    val nextMessageUrl = lambdaConfig.nextInvocationUrl
    val request: Request[Either[String, String], Any] = basicRequest
      .get(uri"$nextMessageUrl")

    for {
      response <- runRequest(request)
      headers = response.headers
      maybeRequestIdHeader = headers.find(_.name.toLowerCase == requestIdHeaderName)
      headerRequestId <- maybeRequestIdHeader match {
        case Some(requestIdHeader) => ZIO.succeed(requestIdHeader.value)
        case None =>
          ZIO.fail(
            NextMessageError(None, s"No request id was found in the headers for the next message - $headers", None)
          )
      }
      result <- parseNewMessageBody(response.body, nextMessageUrl, headerRequestId)
    } yield NextMessageResponse(headerRequestId, result)

  }.mapError {
    case nextMessageError: NextMessageError => nextMessageError
    case error => NextMessageError(None, "Error getting next message", Some(error))
  }

  private def parseNewMessageBody[A](response: Either[String, String], nextMessageUrl: String, headerRequestId: String)(
      implicit bodyDecoder: Decoder[A]
  ): ZIO[Any, NextMessageError, List[SqsDecoding[A]]] = {
    for {
      responseText <- ZIO
        .fromEither(response)
        .logError(s"$nextMessageUrl failed with $response")
        .mapError(error =>
          NextMessageError(
            maybeRequestId = Some(headerRequestId),
            message = s"$headerRequestId headerRequestId failed reading",
            maybeCause = Some(new RuntimeException(s"Service returned $error for $nextMessageUrl"))
          )
        )
      sqsDecoding <- ZIO
        .fromEither {
          SqsDecoding.decodeMany(responseText).left.map { error =>
            NextMessageError(
              maybeRequestId = Some(headerRequestId),
              s"$headerRequestId headerRequestId failed decoding from $responseText",
              Some(error)
            )
          }
        }
        .logError(s"Failed decoding message text $responseText")
    } yield sqsDecoding
  }

  private def runRequest[A](request: Request[A, Any]): ZIO[Any, Throwable, Response[A]] = {
    for {
      _ <- ZIO.log(s"Calling lambda using ${request.method}:${request.uri}")
      backend <- HttpClientZioBackend()
      result <- request.send(backend)
    } yield result
  }

  def sendInvocationResponse(
      requestId: String,
      message: String
  ): ZIO[Any, Throwable, Response[Either[String, String]]] = {
    val uri = lambdaConfig.invocationResponseUrlFormat.format(requestId)
    val request: Request[Either[String, String], Any] = basicRequest
      .post(uri"$uri")
      .body(message)

    for {
      _ <- ZIO.log(s"Sending invocation response to $uri - $message")
      invocationResponseResult <- runRequest(request)
    } yield invocationResponseResult

  }

}

package com.github.pbyrne84.zioscalanativelambda

import com.github.pbyrne84.zioscalanativelambda.client.{LambdaHttpClient, NextMessageError, NextMessageResponse}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Json}
import zio.{ULayer, ZIO, ZLayer}

object LamdaSqsService {

  val layer: ULayer[LamdaSqsService] = ZLayer.succeed(new LamdaSqsService())

  def processSqsMessages: ZIO[LambdaHttpClient with LamdaSqsService, Throwable, Unit] = {
    ZIO.serviceWithZIO[LamdaSqsService](_.processSqsMessages)
  }

}

object InvocationResponse {
  import io.circe.generic.semiauto._

  private implicit val invvoationResponseEncoder: Encoder.AsObject[InvocationResponse] =
    deriveEncoder[InvocationResponse]

  def createResponseJson(success: Boolean): Json = {
    if (success) {
      InvocationResponse("SUCCESS").asJson
    } else {
      InvocationResponse("FAILED").asJson
    }
  }
}

case class InvocationResponse(status: String)

class LamdaSqsService {

  def processSqsMessages: ZIO[LambdaHttpClient, Throwable, Unit] = {
    implicit val sqsMessageStringDecoder: Decoder[String] = Decoder.decodeString

    for {
      _ <- ZIO.log("getting next message from sqs")
      nextMessage <- LambdaHttpClient.getNextMessage.foldZIO(
        error => deleteInvalidRequestIfPossible(error),
        success => ZIO.succeed(success)
      )
      invocationResponse = InvocationResponse.createResponseJson(true)
      _ <- LambdaHttpClient.sendInvocationResponse(nextMessage.headerRequestId, invocationResponse.spaces2)
    } yield ()
  }

  private def deleteInvalidRequestIfPossible[A](
      errorCause: NextMessageError
  ): ZIO[LambdaHttpClient, Throwable, NextMessageResponse[A]] = {
    errorCause.maybeRequestId match {
      case Some(headerRequestId) =>
        for {
          _ <- ZIO.log(s"Attempting to delete request id $headerRequestId as message had problems")
          invocationResponse = InvocationResponse.createResponseJson(false)
          _ <- LambdaHttpClient
            .sendInvocationResponse(headerRequestId, invocationResponse.spaces2)
            .logError(s"failed deleting message $headerRequestId")
          // left will always fail but we need to satisfy the type gods
          dummyResult <- ZIO.fromEither[NextMessageError, NextMessageResponse[A]](Left(errorCause))
        } yield dummyResult

      case None => {
        for {
          _ <- ZIO.log("No request id was returned in the headers from the next message call")
          result <- ZIO.fail(errorCause)
        } yield result

      }
    }

  }

}

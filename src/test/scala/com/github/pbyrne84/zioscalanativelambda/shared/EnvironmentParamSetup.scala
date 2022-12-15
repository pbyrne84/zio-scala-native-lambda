package com.github.pbyrne84.zioscalanativelambda.shared

import com.typesafe.scalalogging.StrictLogging
import zio.{Ref, ZIO, ZLayer}

import java.net.ServerSocket
import scala.util.{Try, Using}

object InitialisedParams {
  val layer = ZLayer(for {
    environmentParamSetup <- ZIO.service[EnvironmentParamSetup]
    initialisedParams <- environmentParamSetup.setup
  } yield initialisedParams)

  val empty: InitialisedParams = InitialisedParams(-1)

  val getParams: ZIO[EnvironmentParamSetup, Nothing, InitialisedParams] =
    EnvironmentParamSetup.setup

}
case class InitialisedParams(amazonLambdaPort: Int) {
  val isEmpty: Boolean = amazonLambdaPort == -1
}

object EnvironmentParamSetup {

  private val emptyParams: InitialisedParams = InitialisedParams(-1)
  val value = Ref.Synchronized.make(1)

  val layer: ZLayer[Any, Nothing, EnvironmentParamSetup] = ZLayer {
    for {
      maybeInitialisedParamsRef <- Ref.Synchronized.make(emptyParams)
    } yield new EnvironmentParamSetup(maybeInitialisedParamsRef)
  }

  def setup: ZIO[EnvironmentParamSetup, Nothing, InitialisedParams] = {
    ZIO.serviceWithZIO[EnvironmentParamSetup](_.setup)
  }

}

class EnvironmentParamSetup(
    maybeInitialisedParamsRef: Ref.Synchronized[InitialisedParams]
) extends StrictLogging {

  def setup: ZIO[Any, Nothing, InitialisedParams] = {
    for {
      // As mentioned in the read me depending on how you run the test affects all the ref stuff.
      // If running via main as the tests all are objects so the default in Intellij with the plugin
      // then everything seems to initialise 3 times versus 1. Kooky.
      maybeInitialisedParams <- maybeInitialisedParamsRef.modify { initialisedParams: InitialisedParams =>
        if (initialisedParams.isEmpty) {
          initialiseParams match {
            case Left(_) =>
              logger.info("failed setting up params")
              (initialisedParams, initialisedParams)
            case Right(value) =>
              logger.info(s"setting value $value")
              (value, value)
          }
        } else {
          logger.info("found params")
          (initialisedParams, initialisedParams)
        }
      }
    } yield maybeInitialisedParams

  }

  private def initialiseParams: Either[Throwable, InitialisedParams] = {
    def serverPortCalculationOperation[A](
        mappingCall: Int => A
    ): Either[Throwable, A] = {
      Using(new ServerSocket(0)) { serverSocket: ServerSocket =>
        val result = mappingCall(serverSocket.getLocalPort)
        result
      }.toEither
    }

    for {
      wireMockPort <- serverPortCalculationOperation { (port: Int) =>
        val awsLambdaRuntimeApiPort = "AWS_LAMBDA_RUNTIME_API_PORT"
        def initialisePort = {
          System.setProperty(awsLambdaRuntimeApiPort, port.toString)
        }

        Option(System.getProperty(awsLambdaRuntimeApiPort)).map { maybeValue =>
          Try(maybeValue.toInt).toEither
        } match {
          case Some(errorOrPort) =>
            errorOrPort match {
              case Left(error) =>
                logger.error(s"$awsLambdaRuntimeApiPort was invalid setting to $port", error)
                initialisePort
                port
              case Right(foundPort) =>
                logger.info(s"$awsLambdaRuntimeApiPort was already initialised to $foundPort")
                foundPort
            }
          case None =>
            logger.info(s"$awsLambdaRuntimeApiPort not found setting to $port")
            initialisePort
            port
        }

      }
    } yield InitialisedParams(wireMockPort)

  }

}

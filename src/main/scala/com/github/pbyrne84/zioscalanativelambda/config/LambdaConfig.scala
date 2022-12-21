package com.github.pbyrne84.zioscalanativelambda.config

import com.typesafe.config.{ConfigFactory, ConfigParseOptions}
import zio.config.typesafe.TypesafeConfig
import zio.{Layer, ZIO}

object LambdaConfig {

  import zio.config._
  import ConfigDescriptor._

  val layer: Layer[ReadError[String], LambdaConfig] =
    TypesafeConfig.fromTypesafeConfig(
      configTask,
      remoteServicesDescriptor
    )

  private lazy val configTask = {
    ZIO.attempt(ConfigFactory.load(ConfigParseOptions.defaults().setAllowMissing(true)))
  }

  lazy val remoteServicesDescriptor: _root_.zio.config.ConfigDescriptor[LambdaConfig] =
    nested("lambda") {
      string("nextInvocationUrl") zip string("invocationResponseUrlFormat")
    }.to[LambdaConfig]

}

case class LambdaConfig(nextInvocationUrl: String, invocationResponseUrlFormat: String)

package com.github.pbyrne84.zioscalanativelambda.shared.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import zio.{Task, ZIO}

object TestWireMock {}

class TestWireMock(val port: Int) {

  lazy val wireMock = new WireMockServer(port)

  def reset: Task[Unit] = {
    ZIO.attemptBlocking {
      if (!wireMock.isRunning) {
        wireMock.start()
      }

      wireMock.resetAll()
    }
  }

}

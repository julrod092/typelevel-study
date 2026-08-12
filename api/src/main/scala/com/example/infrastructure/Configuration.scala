package com.example.infrastructure

import org.http4s.implicits.*
import cats.syntax.all.*
import cats.effect.Async
import com.comcast.ip4s.*
import com.example.infrastructure.routes.Routes
import org.http4s.server.middleware.Logger
import org.http4s.ember.server.EmberServerBuilder

object Configuration {

  def run[F[_]: Async]: F[Nothing] = {
    for {
      routes <- Routes.impl[F]
      finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(routes.orNotFound)
      _ <- EmberServerBuilder
        .default[F]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(finalHttpApp)
        .build
    } yield ()
  }.useForever
}

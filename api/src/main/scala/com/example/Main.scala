package com.example

import cats.effect.{IO, IOApp}
import com.example.infrastructure.configuration.Configuration

object Main extends IOApp.Simple {
  val run: IO[Nothing] = Configuration.run[IO]
}

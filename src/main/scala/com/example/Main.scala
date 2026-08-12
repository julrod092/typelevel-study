package com.example

import cats.effect._

object Main extends IOApp.Simple {
  val run: IO[Nothing] = infrastructure.Configuration.run[IO]
}

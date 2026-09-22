package com.example.infrastructure.tracing

import cats.effect.IO
import io.circe.Json
import natchez.EntryPoint
import natchez.log.Log
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait TracingLogger {
  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val entryPoint: EntryPoint[IO] =
    Log.entryPoint[IO]("order-priced-stream-processor", (json: Json) => json.noSpaces)

}

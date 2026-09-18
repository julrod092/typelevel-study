package com.example.infrastructure.tracing

import cats.effect.IO
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import com.example.events.EventResult
import com.example.eventhandler.configuration.{Configuration, Dependencies}
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import natchez.log.Log
import natchez.{EntryPoint, Span, Trace, TraceValue}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait TracingLogger {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val entryPoint: EntryPoint[IO] =
    Log.entryPoint[IO]("order-priced-stream-processor", (json: Json) => json.noSpaces)

  def init[A](dynamodbEvent: DynamodbEvent, context: Context)(f: DynamodbEvent => Dependencies[IO] => IO[EventResult]): IO[Unit] = entryPoint
    .root("dynamodb-stream-batch", Span.Options.Defaults.withSpanKind(Span.SpanKind.Consumer))
    .use { rootSpan =>
      for {
        trace <- Trace.ioTrace(rootSpan)
        result <- Configuration.dependencies[IO](trace).use(env => f(dynamodbEvent)(env))
      } yield {
        result.failures.foreach(error => logger.error(error.asJson.noSpaces))
        trace.put(
          "aws.request_id" -> TraceValue.StringValue(
            Option(context.getAwsRequestId).getOrElse("unknown")
          ),
          "event.totalCount" -> TraceValue.NumberValue(result.totalRecords),
          "event.success" -> TraceValue.NumberValue(result.successes.size),
          "event.failures" -> TraceValue.NumberValue(result.failures.size)
        )
      }
    }
}

package com.example

import cats.data.EitherT
import cats.effect.unsafe.implicits.global
import cats.effect.{Async, IO}
import cats.implicits.*
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.example.events.EventHandlerError.{LambdaError, PublishError}
import com.example.events.{EventHandlerError, EventResult, OrderPricedEvent}
import com.example.infrastructure.configuration.Dependencies
import com.example.infrastructure.decoders.OrderPricedDecoder
import com.example.infrastructure.tracing.TracingLogger
import fs2.{Chunk, Stream}

import scala.jdk.CollectionConverters.*

final class StreamProcessorHandler
    extends RequestHandler[DynamodbEvent, Unit]
    with TracingLogger {

  private def validate[F[_]: Async](
      event: DynamodbEvent
  ): EitherT[F, EventHandlerError, List[DynamodbStreamRecord]] =
    EitherT.cond[F](
      Option(event.getRecords).map(_.size()).getOrElse(0) > 0,
      event.getRecords.asScala.toList,
      EventHandlerError.EmptyEvents("0 events from last run")
    )

  private def publish[F[_]: Async](
      batch: Chunk[Either[EventHandlerError, OrderPricedEvent]]
  )(using env: Dependencies[F]): F[EventResult] =
    batch
      .traverse {
        case Left(value)  => Async[F].pure(Left(value))
        case Right(value) =>
          env.publisher
            .publish(value)
            .redeem(
              err => Left(PublishError(err.getMessage, value.eventId)),
              _ => Right(value)
            )
      }
      .map(_.foldLeft(EventResult.empty) {
        case (acc, Left(value)) => acc.copy(failures = EventResult.fromError(value) :: acc.failures)
        case (acc, Right(value)) => acc.copy(successes = value.eventId :: acc.successes)
      })

  private def process[F[_]: Async](
      eventRecords: List[DynamodbStreamRecord]
  )(using env: Dependencies[F]): F[EventResult] =
    Stream
      .emits(eventRecords)
      .map(OrderPricedDecoder.decode)
      .groupWithin(env.stream.chunkSize, env.stream.chunkWindow)
      .evalMap[F, EventResult](publish)
      .reduce[EventResult](EventResult.reduceBatch)
      .compile
      .lastOrError
      .redeem(
        err => EventResult.fromOnlyErrors(LambdaError(message = err.getMessage)),
        identity
      )

  private def run[F[_]: Async](event: DynamodbEvent)(using env: Dependencies[F]): F[EventResult] =
    validate[F](event).value
      .flatMap(
        _.fold(err => Async[F].pure(EventResult.fromOnlyErrors(err)), records => process(records))
      )

  override def handleRequest(event: DynamodbEvent, context: Context): Unit =
    init(event, context)(event => env => run[IO](event)(using env = env))
      .unsafeRunSync()
}

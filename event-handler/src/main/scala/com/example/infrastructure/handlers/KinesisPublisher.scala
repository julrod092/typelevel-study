package com.example.infrastructure.handlers

import cats.effect.Async
import cats.syntax.functor.*
import com.example.events.OrderPricedEvent
import io.circe.syntax.*
import io.circe.generic.auto.*
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest

import java.nio.charset.StandardCharsets

trait EventPublisher[F[_]] {
  def publish(event: OrderPricedEvent): F[Unit]
}

object KinesisPublisher {

  def apply[F[_]: Async](
      client: KinesisAsyncClient,
      streamName: String
  ): EventPublisher[F] =
    (event: OrderPricedEvent) => Async[F]
      .fromCompletableFuture(
        Async[F].delay {
          val request = PutRecordRequest
            .builder()
            .streamName(streamName)
            .partitionKey(event.orderId)
            .data(
              SdkBytes.fromString(
                event.asJson.noSpaces,
                StandardCharsets.UTF_8
              )
            )
            .build()

          client.putRecord(request)
        }
      )
      .void
}

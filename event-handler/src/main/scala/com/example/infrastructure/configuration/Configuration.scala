package com.example.infrastructure.configuration

import cats.effect.{Async, Resource}
import com.example.infrastructure.handlers.{EventPublisher, KinesisPublisher}
import natchez.Trace
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient

import scala.concurrent.duration.FiniteDuration

final case class StreamConfig(
    name: String,
    parallelism: Int,
    chunkSize: Int,
    chunkWindow: FiniteDuration
)

final case class Dependencies[F[_]](
    publisher: EventPublisher[F],
    trace: Trace[F],
    stream: StreamConfig
)

object Configuration {

  private def kinesisClient[F[_]: Async](
      config: AppConfig
  ): Resource[F, KinesisAsyncClient] =
    Resource.fromAutoCloseable(
      Async[F].delay {
        val builder = KinesisAsyncClient
          .builder()
          .httpClientBuilder(NettyNioAsyncHttpClient.builder())
          .region(config.region)

        config.endpoint.foreach(builder.endpointOverride)
        builder.build()
      }
    )

  def dependencies[F[_]: Async](trace: Trace[F]): Resource[F, Dependencies[F]] =
    for {
      config <- AppConfig.config.resource[F]
      kinesis <- kinesisClient[F](config)
    } yield Dependencies(
      publisher = KinesisPublisher(kinesis, config.streamName),
      trace = trace,
      stream = StreamConfig(
        name = config.streamName,
        parallelism = config.parallelism,
        chunkSize = config.streamChunkSize,
        chunkWindow = config.streamChunkTimeWindow
      )
    )
}

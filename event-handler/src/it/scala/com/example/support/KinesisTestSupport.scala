package com.example.support

import cats.effect.{IO, Resource}
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.{
  CreateStreamRequest,
  DeleteStreamRequest,
  DescribeStreamRequest,
  GetRecordsRequest,
  GetShardIteratorRequest,
  Record,
  ShardIteratorType,
  StreamStatus
}

import java.util.concurrent.CompletableFuture
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

object KinesisTestSupport {

  def stream(client: KinesisAsyncClient): Resource[IO, String] =
    Resource
      .make(
        UUIDGen[IO].randomUUID.flatMap { id =>
          val name = s"it-events-$id"
          await(
            client.createStream(
              CreateStreamRequest.builder().streamName(name).shardCount(1).build()
            )
          ).as(name)
        }
      )(name =>
        await(
          client.deleteStream(
            DeleteStreamRequest.builder().streamName(name).build()
          )
        ).void
      )
      .evalTap(name => awaitActive(client, name))

  def latestIterator(client: KinesisAsyncClient, streamName: String): IO[String] =
    for {
      stream <- await(
        client.describeStream(
          DescribeStreamRequest.builder().streamName(streamName).build()
        )
      )
      iterator <- await(
        client.getShardIterator(
          GetShardIteratorRequest
            .builder()
            .streamName(streamName)
            .shardId(stream.streamDescription().shards().get(0).shardId())
            .shardIteratorType(ShardIteratorType.LATEST)
            .build()
        )
      )
    } yield iterator.shardIterator()

  def readRecords(
      client: KinesisAsyncClient,
      iterator: String,
      expectedCount: Int
  ): IO[List[Record]] = {
    def poll(next: String, collected: List[Record]): IO[List[Record]] =
      await(
        client.getRecords(
          GetRecordsRequest.builder().shardIterator(next).build()
        )
      ).flatMap { response =>
        val records = collected ++ response.records().asScala.toList
        if (records.size >= expectedCount) IO.pure(records)
        else IO.sleep(250.millis) *> poll(response.nextShardIterator(), records)
      }

    poll(iterator, Nil).timeoutTo(
      20.seconds,
      IO.raiseError(new AssertionError(s"Timed out waiting for $expectedCount Kinesis records"))
    )
  }

  private def awaitActive(client: KinesisAsyncClient, streamName: String): IO[Unit] = {
    def poll: IO[Unit] =
      await(
        client.describeStream(
          DescribeStreamRequest.builder().streamName(streamName).build()
        )
      ).flatMap { response =>
        if (response.streamDescription().streamStatus() == StreamStatus.ACTIVE) IO.unit
        else IO.sleep(250.millis) *> poll
      }

    poll.timeoutTo(
      20.seconds,
      IO.raiseError(new AssertionError(s"Kinesis stream $streamName did not become active"))
    )
  }

  private def await[A](future: => CompletableFuture[A]): IO[A] =
    IO.fromCompletableFuture(IO.delay(future))
}

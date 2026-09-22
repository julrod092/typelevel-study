package com.example.infrastructure.handlers

import cats.effect.IO
import cats.effect.std.UUIDGen
import com.example.generators.EventGenerators
import com.example.generators.EventGenerators.given
import com.example.infrastructure.decoders.OrderPricedDecoder
import com.example.support.EventTestSupport.*
import com.example.support.{KinesisIntegrationSuite, KinesisTestSupport}
import io.circe.parser.parse
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException

object KinesisPublisherIntegrationSuite extends KinesisIntegrationSuite {

  test("generated events retain their JSON contract and partition key in Kinesis") { fixture =>
    KinesisTestSupport.stream(fixture.client).use { streamName =>
      forall(EventGenerators.orderPricedEvent) { event =>
        for {
          iterator <- KinesisTestSupport.latestIterator(fixture.client, streamName)
          _ <- KinesisPublisher[IO](fixture.client, streamName).publish(event)
          records <- KinesisTestSupport.readRecords(fixture.client, iterator, expectedCount = 1)
        } yield expect(records.size == 1) and
          expect(records.map(_.partitionKey()) == List(event.orderId)) and
          expect(
            records.map(record => parse(record.data().asUtf8String())) ==
              List(Right(expectedJson(event)))
          )
      }
    }
  }

  // Component integration only: the fixture matches the decoder, not the Orders table.
  test("a decoder-compatible INSERT can be decoded and published to Kinesis") { fixture =>
    val event = sampleEvent.copy(customerId = "customer-東京")
    val record = streamRecord(decoderImage(event))

    KinesisTestSupport.stream(fixture.client).use { streamName =>
      for {
        decoded <- IO.fromEither(
          OrderPricedDecoder
            .decode(record)
            .left
            .map(error => new AssertionError(s"Expected a decoder-compatible image: $error"))
        )
        iterator <- KinesisTestSupport.latestIterator(fixture.client, streamName)
        _ <- KinesisPublisher[IO](fixture.client, streamName).publish(decoded)
        records <- KinesisTestSupport.readRecords(fixture.client, iterator, expectedCount = 1)
      } yield expect(decoded == event) and
        expect(records.map(_.partitionKey()) == List(event.orderId)) and
        expect(
          records.map(record => parse(record.data().asUtf8String())) ==
            List(Right(expectedJson(event)))
        )
    }
  }

  test("publishing to a missing stream preserves the real SDK failure") { fixture =>
    for {
      id <- UUIDGen[IO].randomUUID
      result <- KinesisPublisher[IO](fixture.client, s"missing-$id").publish(sampleEvent).attempt
    } yield expect(result.left.exists(_.isInstanceOf[ResourceNotFoundException]))
  }
}

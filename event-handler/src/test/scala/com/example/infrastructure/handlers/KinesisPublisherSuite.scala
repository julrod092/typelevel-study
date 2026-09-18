package com.example.infrastructure.handlers

import cats.effect.IO
import cats.effect.testkit.TestControl
import com.example.generators.EventGenerators
import com.example.generators.EventGenerators.given
import com.example.support.EventTestSupport.{expectedJson, sampleEvent}
import com.example.support.KinesisClientStub
import io.circe.parser.parse
import software.amazon.awssdk.services.kinesis.model.{PutRecordRequest, PutRecordResponse}
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

object KinesisPublisherSuite extends SimpleIOSuite with Checkers {

  test("publishes the configured stream, order partition key and JSON contract") {
    val scenario = for {
      event <- EventGenerators.orderPricedEvent
      streamName <- EventGenerators.streamName
    } yield (event, streamName)

    forall(scenario) { case (event, streamName) =>
      val captured = new AtomicReference[PutRecordRequest]()
      val client = KinesisClientStub { request =>
        captured.set(request)
        CompletableFuture.completedFuture(PutRecordResponse.builder().build())
      }

      KinesisPublisher[IO](client, streamName).publish(event).map { _ =>
        val request = captured.get()
        expect(request.streamName() == streamName) and
          expect(request.partitionKey() == event.orderId) and
          expect(parse(request.data().asUtf8String()) == Right(expectedJson(event)))
      }
    }
  }

  test("preserves UTF-8 characters and JSON escaping") {
    val event = sampleEvent.copy(orderId = "order-ñ", customerId = "customer-東京\"\n")
    val captured = new AtomicReference[PutRecordRequest]()
    val client = KinesisClientStub { request =>
      captured.set(request)
      CompletableFuture.completedFuture(PutRecordResponse.builder().build())
    }

    KinesisPublisher[IO](client, "unicode-events").publish(event).map { _ =>
      val request = captured.get()
      val json = new String(request.data().asByteArray(), StandardCharsets.UTF_8)
      expect(request.partitionKey() == event.orderId) and
        expect(parse(json) == Right(expectedJson(event)))
    }
  }

  test("waits for the SDK future before completing publication") {
    val response = new CompletableFuture[PutRecordResponse]()
    val invoked = new AtomicBoolean(false)
    val client = KinesisClientStub { _ =>
      invoked.set(true)
      response
    }

    for {
      control <- TestControl.execute(
        KinesisPublisher[IO](client, "pending-events").publish(sampleEvent)
      )
      _ <- control.tick
      pending <- control.results
      wasInvoked <- IO(invoked.get())
      _ <- IO(response.complete(PutRecordResponse.builder().build()))
      _ <- control.tickAll
      completed <- control.results
    } yield expect(wasInvoked) and expect(pending.isEmpty) and
      expect(completed.exists(_.isSuccess))
  }

  test("propagates the original failed future error") {
    val publishFailure = new RuntimeException("kinesis unavailable")
    val client = KinesisClientStub(_ => CompletableFuture.failedFuture(publishFailure))

    KinesisPublisher[IO](client, "failed-events").publish(sampleEvent).attempt.map {
      case Left(error) => expect(error eq publishFailure)
      case Right(_)    => failure("Expected publishing to fail")
    }
  }

  test("defers a synchronous putRecord failure until the returned IO is executed") {
    val invoked = new AtomicBoolean(false)
    val publishFailure = new RuntimeException("synchronous putRecord failure")
    val client = KinesisClientStub { _ =>
      invoked.set(true)
      throw publishFailure
    }
    val publish = KinesisPublisher[IO](client, "failed-events").publish(sampleEvent)
    val wasLazy = !invoked.get()

    publish.attempt.map {
      case Left(error) =>
        expect(wasLazy) and expect(invoked.get()) and expect(error eq publishFailure)
      case Right(_) => failure("Expected publishing to fail")
    }
  }

  test("defers request encoding and construction until the returned IO is executed") {
    val invoked = new AtomicBoolean(false)
    val client = KinesisClientStub { _ =>
      invoked.set(true)
      CompletableFuture.completedFuture(PutRecordResponse.builder().build())
    }
    val publisher = KinesisPublisher[IO](client, "invalid-events")

    IO.delay(publisher.publish(null)).attempt.flatMap {
      case Left(error) =>
        IO.pure(failure(s"Constructing the publish IO threw ${error.getClass.getName}"))
      case Right(publish) =>
        val constructionWasLazy = !invoked.get()
        publish.attempt.map {
          case Left(_)  => expect(constructionWasLazy) and expect(!invoked.get())
          case Right(_) => failure("Expected request preparation to fail for a null event")
        }
    }
  }
}

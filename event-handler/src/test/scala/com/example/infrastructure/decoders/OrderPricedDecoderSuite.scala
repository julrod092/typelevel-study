package com.example.infrastructure.decoders

import cats.effect.IO
import cats.syntax.all.*
import com.example.events.EventHandlerError.{AttributeError, EmptyPayload, IncorrectEventType}
import com.example.generators.EventGenerators
import com.example.generators.EventGenerators.given
import com.example.support.EventTestSupport.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

import java.time.format.DateTimeParseException

object OrderPricedDecoderSuite extends SimpleIOSuite with Checkers {

  test("decodes every field of a valid INSERT image") {
    val scenario = for {
      event <- EventGenerators.orderPricedEvent
      sequence <- EventGenerators.sequenceNumber
    } yield (event, sequence)

    forall(scenario) { case (event, sequence) =>
      val record = streamRecord(decoderImage(event), sequence)
      expect(OrderPricedDecoder.decode(record) == Right(event))
    }
  }

  test("ignores unrelated image attributes") {
    forall(EventGenerators.orderPricedEvent) { event =>
      val image = decoderImage(event).updated("unrelated", stringAttribute("ignored"))
      expect(OrderPricedDecoder.decode(streamRecord(image)) == Right(event))
    }
  }

  test("reports each missing required attribute with its sequence number") {
    val scenario = for {
      event <- EventGenerators.orderPricedEvent
      sequence <- EventGenerators.sequenceNumber
    } yield (event, sequence)

    forall(scenario) { case (event, sequence) =>
      val messages = Map(
        "eventId" -> "String eventId on payload is empty",
        "orderId" -> "String orderId on payload is empty",
        "customerId" -> "String customerId on payload is empty",
        "subtotal" -> "Amount subtotal is due empty or incorrect",
        "discount" -> "Amount discount is due empty or incorrect",
        "total" -> "Amount total is due empty or incorrect",
        "createdAt" -> "Instant createdAt is due empty or incorrect"
      )
      messages.toList
        .map { case (key, message) =>
          val record = streamRecord(decoderImage(event) - key, sequence)
          expect(OrderPricedDecoder.decode(record) == Left(AttributeError(message, sequence)))
        }
        .reduce(_ and _)
    }
  }

  test("reports a missing DynamoDB payload") {
    val record = streamRecord(decoderImage(sampleEvent))
    record.setDynamodb(null)
    IO.pure(expect(OrderPricedDecoder.decode(record) == Left(EmptyPayload("Empty event"))))
  }

  test("reports a missing sequence number") {
    val record = streamRecord(decoderImage(sampleEvent), sequenceNumber = null)
    IO.pure(expect(OrderPricedDecoder.decode(record) == Left(EmptyPayload("Empty event"))))
  }

  test("retains the sequence number when the new image is missing") {
    forall(EventGenerators.sequenceNumber) { sequence =>
      val record = streamRecord(decoderImage(sampleEvent), sequence)
      record.getDynamodb.setNewImage(null)
      expect(
        OrderPricedDecoder.decode(record) ==
          Left(EmptyPayload(s"Empty payload in Event $sequence", Some(sequence)))
      )
    }
  }

  test("reports a missing event name") {
    val record = streamRecord(decoderImage(sampleEvent), eventName = null)
    IO.pure(
      expect(
        OrderPricedDecoder.decode(record) ==
          Left(IncorrectEventType("Error reading event type"))
      )
    )
  }

  // These characterize current limitations; see TESTING.md before changing the expected behavior.
  test("the current decoder rejects MODIFY even when the image is complete") {
    forall(EventGenerators.orderPricedEvent) { event =>
      val record = streamRecord(decoderImage(event), eventName = "MODIFY")
      expect(
        OrderPricedDecoder.decode(record) ==
          Left(IncorrectEventType("Error reading event type"))
      )
    }
  }

  test("the current decoder throws for REMOVE instead of returning a typed error") {
    val record = streamRecord(decoderImage(sampleEvent), eventName = "REMOVE")
    IO.delay(OrderPricedDecoder.decode(record)).attempt.map { result =>
      expect(result.left.exists(_.isInstanceOf[IllegalArgumentException]))
    }
  }

  test("the current decoder throws for malformed amounts instead of returning AttributeError") {
    List("subtotal", "discount", "total")
      .traverse { key =>
        val image = decoderImage(sampleEvent).updated(key, numberAttribute("not-a-number"))
        IO.delay(OrderPricedDecoder.decode(streamRecord(image))).attempt.map { result =>
          expect(result.left.exists(_.isInstanceOf[NumberFormatException]))
        }
      }
      .map(_.reduce(_ and _))
  }

  test("the current decoder throws for a malformed timestamp instead of returning AttributeError") {
    val image = decoderImage(sampleEvent).updated("createdAt", stringAttribute("not-an-instant"))
    IO.delay(OrderPricedDecoder.decode(streamRecord(image))).attempt.map { result =>
      expect(result.left.exists(_.isInstanceOf[DateTimeParseException]))
    }
  }

  test("the current decoder cannot consume an Orders image without an image-level eventId") {
    forall(EventGenerators.orderPricedEvent) { event =>
      val record = streamRecord(persistedOrderImage(event))
      expect(
        OrderPricedDecoder.decode(record) ==
          Left(AttributeError("String eventId on payload is empty", "123456789"))
      )
    }
  }
}

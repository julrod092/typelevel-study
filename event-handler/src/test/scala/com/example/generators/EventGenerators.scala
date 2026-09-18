package com.example.generators

import cats.Show
import com.example.events.{EventHandlerError, EventResult, FailedEventDetail, OrderPricedEvent}
import com.example.events.EventHandlerError.*
import org.scalacheck.Gen

object EventGenerators {

  given Show[OrderPricedEvent] = Show.fromToString
  given Show[EventHandlerError] = Show.fromToString
  given Show[FailedEventDetail] = Show.fromToString
  given Show[EventResult] = Show.fromToString

  val orderPricedEvent: Gen[OrderPricedEvent] = for {
    eventId <- DomainGenerators.nonEmptyString
    orderId <- DomainGenerators.nonEmptyString
    customerId <- DomainGenerators.nonEmptyString
    subtotal <- DomainGenerators.positiveAmount
    discountCents <- Gen.choose(0L, (subtotal * 100).toLong)
    createdAt <- DomainGenerators.instant
    nanos <- Gen.choose(0, 999999999)
    discount = BigDecimal(discountCents) / 100
  } yield OrderPricedEvent(
    eventId,
    orderId,
    customerId,
    subtotal,
    discount,
    subtotal - discount,
    createdAt.plusNanos(nanos.toLong)
  )

  val sequenceNumber: Gen[String] = Gen.choose(1L, Long.MaxValue).map(_.toString)

  val streamName: Gen[String] = DomainGenerators.nonEmptyString.map(value => s"test-$value")

  val errorWithId: Gen[(EventHandlerError, Option[String])] = for {
    message <- DomainGenerators.nonEmptyString
    eventId <- DomainGenerators.nonEmptyString
    error <- Gen.oneOf[(EventHandlerError, Option[String])](
      LambdaError(message) -> None,
      EmptyEvents(message) -> None,
      IncorrectEventType(message) -> None,
      AttributeError(message, eventId) -> Some(eventId),
      EmptyPayload(message) -> None,
      EmptyPayload(message, Some(eventId)) -> Some(eventId),
      PublishError(message, eventId) -> Some(eventId)
    )
  } yield error

  val eventResult: Gen[EventResult] = for {
    successCount <- Gen.choose(0, 8)
    failureCount <- Gen.choose(0, 8)
    successes <- Gen.listOfN(successCount, DomainGenerators.nonEmptyString)
    errors <- Gen.listOfN(failureCount, errorWithId)
  } yield EventResult(
    successCount + failureCount,
    errors.map { case (error, eventId) => FailedEventDetail(eventId, error) },
    successes
  )
}

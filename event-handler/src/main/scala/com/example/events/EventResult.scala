package com.example.events

import cats.implicits.catsSyntaxOptionId
import com.example.events.EventHandlerError.*

case class FailedEventDetail(
    eventId: Option[String] = None,
    error: EventHandlerError
)

case class EventResult(
    totalRecords: Int,
    failures: List[FailedEventDetail],
    successes: List[String]
)

object EventResult {
  def empty: EventResult = EventResult(
    totalRecords = 0,
    failures = List.empty,
    successes = List.empty
  )

  def fromError(error: EventHandlerError): FailedEventDetail = error match {
    case error: LambdaError                 => FailedEventDetail(error = error)
    case error: EmptyEvents                 => FailedEventDetail(error = error)
    case error: IncorrectEventType          => FailedEventDetail(error = error)
    case error @ AttributeError(_, eventId) =>
      FailedEventDetail(eventId = eventId.some, error = error)
    case error @ EmptyPayload(_, eventId) => FailedEventDetail(eventId = eventId, error = error)
    case error @ PublishError(_, eventId) =>
      FailedEventDetail(eventId = eventId.some, error = error)
  }

  def fromOnlyErrors(errors: EventHandlerError*): EventResult = {
    val detailEvents = errors.map(fromError)
    EventResult(failures = detailEvents.toList, successes = List.empty, totalRecords = 0)
  }

  def reduceBatch(a: EventResult, b: EventResult): EventResult = EventResult(
    totalRecords = a.totalRecords + b.totalRecords,
    failures = a.failures ++ b.failures,
    successes = a.successes ++ b.successes
  )
}

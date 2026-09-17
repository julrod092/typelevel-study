package com.example.infrastructure.decoders

import cats.implicits.*
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue
import com.example.events.{EventHandlerError, OrderPricedEvent}
import EventHandlerError.{AttributeError, EmptyPayload, IncorrectEventType}

import java.time.Instant
import scala.jdk.CollectionConverters.*

enum ValidEvent {
  case INSERT, MODIFY
}

object OrderPricedDecoder {

  private def getString(
      label: String,
      attributes: Map[String, AttributeValue]
  )(using sequenceNumber: String): Either[EventHandlerError, String] =
    attributes
      .get(label)
      .map(_.getS)
      .toRight(AttributeError(s"String $label on payload is empty", sequenceNumber))

  private def getBigDecimal(
      label: String,
      attributes: Map[String, AttributeValue]
  )(using sequenceNumber: String): Either[EventHandlerError, BigDecimal] =
    attributes
      .get(label)
      .map(_.getN)
      .map(BigDecimal(_))
      .toRight(AttributeError(s"Amount $label is due empty or incorrect", sequenceNumber))

  private def getInstant(
      label: String,
      attributes: Map[String, AttributeValue]
  )(using sequenceNumber: String): Either[EventHandlerError, Instant] =
    attributes
      .get(label)
      .map(_.getS)
      .map(Instant.parse)
      .toRight(AttributeError(s"Instant $label is due empty or incorrect", sequenceNumber))

  private def transform(
      attributes: Map[String, AttributeValue],
      sequenceNumber: String
  ): Either[EventHandlerError, OrderPricedEvent] = {
    given String = sequenceNumber
    for {
      eventId <- getString("eventId", attributes)
      orderId <- getString("orderId", attributes)
      customerId <- getString("customerId", attributes)
      subtotal <- getBigDecimal("subtotal", attributes)
      discount <- getBigDecimal("discount", attributes)
      total <- getBigDecimal("total", attributes)
      createdAt <- getInstant("createdAt", attributes)
    } yield OrderPricedEvent(
      eventId = eventId,
      orderId = orderId,
      customerId = customerId,
      subtotal = subtotal,
      discount = discount,
      total = total,
      createdAt = createdAt
    )
  }

  private def decodeEvent(
      record: DynamodbStreamRecord
  ): Either[EventHandlerError, OrderPricedEvent] =
    for {
      recordEvent <- Option(record.getDynamodb).toRight(EmptyPayload("Empty event"))
      sequenceNumber <- Option(recordEvent.getSequenceNumber).toRight(EmptyPayload("Empty event"))
      image <- Option(recordEvent.getNewImage).toRight(
        EmptyPayload(
          s"Empty payload in Event ${recordEvent.getSequenceNumber}",
          sequenceNumber.some
        )
      )
      event <- transform(image.asScala.toMap, sequenceNumber)
    } yield event

  def decode(record: DynamodbStreamRecord): Either[EventHandlerError, OrderPricedEvent] =
    Option(record.getEventName).map(ValidEvent.valueOf) match {
      case Some(ValidEvent.INSERT) => decodeEvent(record)
      case _                       => Left(IncorrectEventType("Error reading event type"))
    }
}

package com.example.support

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.{AttributeValue, StreamRecord}
import com.example.events.OrderPricedEvent
import io.circe.Json

import java.time.Instant
import scala.jdk.CollectionConverters.*

object EventTestSupport {

  val sampleEvent: OrderPricedEvent = OrderPricedEvent(
    eventId = "event-123",
    orderId = "order-123",
    customerId = "customer-1",
    subtotal = BigDecimal("89.97"),
    discount = BigDecimal("8.99"),
    total = BigDecimal("80.98"),
    createdAt = Instant.parse("2026-07-22T14:32:00.123456789Z")
  )

  def decoderImage(event: OrderPricedEvent): Map[String, AttributeValue] = Map(
    "eventId" -> stringAttribute(event.eventId),
    "orderId" -> stringAttribute(event.orderId),
    "customerId" -> stringAttribute(event.customerId),
    "subtotal" -> numberAttribute(event.subtotal.toString),
    "discount" -> numberAttribute(event.discount.toString),
    "total" -> numberAttribute(event.total.toString),
    "createdAt" -> stringAttribute(event.createdAt.toString)
  )

  // Models the Orders table schema, which does not yet satisfy the decoder's contract.
  def persistedOrderImage(event: OrderPricedEvent): Map[String, AttributeValue] = Map(
    "orderId" -> stringAttribute(event.orderId),
    "customerId" -> stringAttribute(event.customerId),
    "status" -> stringAttribute("PRICED"),
    "items" -> new AttributeValue().withL(List.empty[AttributeValue].asJava),
    "subtotal" -> numberAttribute(event.subtotal.toString),
    "discountAmount" -> numberAttribute(event.discount.toString),
    "total" -> numberAttribute(event.total.toString),
    "createdAt" -> stringAttribute(event.createdAt.toString),
    "updatedAt" -> stringAttribute(event.createdAt.toString)
  )

  def streamRecord(
      image: Map[String, AttributeValue],
      sequenceNumber: String = "123456789",
      eventName: String = "INSERT"
  ): DynamodbStreamRecord = {
    val dynamodb = new StreamRecord()
    dynamodb.setSequenceNumber(sequenceNumber)
    dynamodb.setNewImage(image.asJava)

    val record = new DynamodbStreamRecord()
    record.setEventID("stream-envelope-id")
    record.setEventName(eventName)
    record.setDynamodb(dynamodb)
    record
  }

  // Specify the wire contract independently of production's derived encoder.
  def expectedJson(event: OrderPricedEvent): Json = Json.obj(
    "eventId" -> Json.fromString(event.eventId),
    "orderId" -> Json.fromString(event.orderId),
    "customerId" -> Json.fromString(event.customerId),
    "subtotal" -> Json.fromBigDecimal(event.subtotal),
    "discount" -> Json.fromBigDecimal(event.discount),
    "total" -> Json.fromBigDecimal(event.total),
    "createdAt" -> Json.fromString(event.createdAt.toString)
  )

  def stringAttribute(value: String): AttributeValue = new AttributeValue().withS(value)

  def numberAttribute(value: String): AttributeValue = new AttributeValue().withN(value)
}

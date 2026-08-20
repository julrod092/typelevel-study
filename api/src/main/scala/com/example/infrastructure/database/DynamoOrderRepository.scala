package com.example.infrastructure.database

import cats.effect.Async
import cats.syntax.all.*
import com.amazonaws.dynamodb.*
import com.example.domain.repositories.{LineItemRecord, OrderRecord, OrdersRepository}

import scala.util.Try

final case class DynamoOrderRepository[F[_]: Async](client: DynamoDB[F], tableName: TableArn)
    extends OrdersRepository[F]
    with BaseDynamoDB[OrderRecord] {

  override val keyAttribute: AttributeName = AttributeName("orderId")

  private def encodeLineItems(item: LineItemRecord): AttributeValue = AttributeValue.m(
    Map(
      AttributeName("sku") -> AttributeValue.s(StringAttributeValue(item.sku)),
      AttributeName("quantity") -> AttributeValue.n(NumberAttributeValue(item.quantity.toString)),
      AttributeName("unitPrice") -> AttributeValue.n(NumberAttributeValue(item.unitPrice.toString)),
      AttributeName("lineTotal") -> AttributeValue.n(NumberAttributeValue(item.lineTotal.toString))
    )
  )

  private def decodeLineItem(value: AttributeValue): Option[LineItemRecord] =
    value.project.m.flatMap { item =>
      for {
        sku <- item.get(AttributeName("sku")).flatMap(_.project.s).map(_.value)
        quantity <- item
          .get(AttributeName("quantity"))
          .flatMap(_.project.n)
          .flatMap(_.value.toIntOption)
        unitPrice <- item
          .get(AttributeName("unitPrice"))
          .flatMap(_.project.n)
          .flatMap(value => Try(BigDecimal(value.value)).toOption)
        lineTotal <- item
          .get(AttributeName("lineTotal"))
          .flatMap(_.project.n)
          .flatMap(value => Try(BigDecimal(value.value)).toOption)
      } yield LineItemRecord(sku, quantity, unitPrice, lineTotal)
    }

  override def encodeRecord(record: OrderRecord): DynamoRecord = {
    val mandatoryValues = Map(
      keyAttribute -> AttributeValue.s(StringAttributeValue(record.orderId)),
      AttributeName("customerId") -> AttributeValue.s(StringAttributeValue(record.customerId)),
      AttributeName("status") -> AttributeValue.s(StringAttributeValue(record.status)),
      AttributeName("items") -> AttributeValue.l(record.items.map(encodeLineItems)),
      AttributeName("subtotal") -> AttributeValue.n(NumberAttributeValue(record.subtotal.toString)),
      AttributeName("discountAmount") -> AttributeValue.n(
        NumberAttributeValue(record.discountAmount.toString)
      ),
      AttributeName("total") -> AttributeValue.n(NumberAttributeValue(record.total.toString)),
      AttributeName("createdAt") -> AttributeValue.s(StringAttributeValue(record.createdAt)),
      AttributeName("updatedAt") -> AttributeValue.s(StringAttributeValue(record.updatedAt))
    )

    record.couponCode.fold(mandatoryValues) { code =>
      mandatoryValues ++ Map(
        AttributeName("couponCode") -> AttributeValue.s(StringAttributeValue(code))
      )
    }
  }

  override def decodeRecord(record: DynamoRecord): Option[OrderRecord] =
    for {
      orderId <- record.get(keyAttribute).flatMap(_.project.s).map(_.value)
      customerId <- record.get(AttributeName("customerId")).flatMap(_.project.s).map(_.value)
      status <- record.get(AttributeName("status")).flatMap(_.project.s).map(_.value)
      encodedItems <- record.get(AttributeName("items")).flatMap(_.project.l)
      items <- encodedItems.traverse(decodeLineItem)
      subtotal <- record
        .get(AttributeName("subtotal"))
        .flatMap(_.project.n)
        .flatMap(value => Try(BigDecimal(value.value)).toOption)
      discountAmount <- record
        .get(AttributeName("discountAmount"))
        .flatMap(_.project.n)
        .flatMap(value => Try(BigDecimal(value.value)).toOption)
      total <- record
        .get(AttributeName("total"))
        .flatMap(_.project.n)
        .flatMap(value => Try(BigDecimal(value.value)).toOption)
      couponCode <- record
        .get(AttributeName("couponCode"))
        .fold[Option[Option[String]]](Some(None))(
          _.project.s.map(value => Some(value.value))
        )
      createdAt <- record.get(AttributeName("createdAt")).flatMap(_.project.s).map(_.value)
      updatedAt <- record.get(AttributeName("updatedAt")).flatMap(_.project.s).map(_.value)
    } yield OrderRecord(
      orderId,
      customerId,
      status,
      items,
      subtotal,
      discountAmount,
      total,
      couponCode,
      createdAt,
      updatedAt
    )

  override def savePricedOrder(record: OrderRecord): F[OrderRecord] =
    client.putItem(tableName, encodeRecord(record)).as(record)
}

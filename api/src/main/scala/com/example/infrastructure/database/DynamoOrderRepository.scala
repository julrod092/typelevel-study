package com.example.infrastructure.database

import cats.effect.Async
import cats.syntax.all.*
import com.amazonaws.dynamodb.*
import com.example.domain.repositories.{LineItemRecord, OrderRecord, OrdersRepository}

final case class DynamoOrderRepository[F[_]: Async](client: DynamoDB[F])
    extends OrdersRepository[F] {

  type DynamoRecord = Map[AttributeName, AttributeValue]
  private val tableName = TableArn("Orders")


  private def encodeLineItems(item: LineItemRecord): AttributeValue = AttributeValue.m(
    Map(
      AttributeName("sku") -> AttributeValue.s(StringAttributeValue(item.sku)),
      AttributeName("quantity") -> AttributeValue.n(NumberAttributeValue(item.quantity.toString)),
      AttributeName("unitPrice") -> AttributeValue.n(NumberAttributeValue(item.unitPrice.toString)),
      AttributeName("lineTotal") -> AttributeValue.n(NumberAttributeValue(item.lineTotal.toString))
    )
  )

  private def encodeOrder(record: OrderRecord): DynamoRecord = {
    val mandatoryValues = Map(
      AttributeName("orderId") -> AttributeValue.s(StringAttributeValue(record.orderId)),
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

  override def savePricedOrder(record: OrderRecord): F[OrderRecord] =
    client
      .putItem(tableName, encodeOrder(record))
      .attempt
      .map(_ => record)
}

package com.example.domain.models

import java.time.Instant

enum OrderStatus(value: String) {
  case Priced extends OrderStatus("PRICED")
  case InProgress extends OrderStatus("IN_PROGRESS")
}

case class LineItem(
    sku: LineItem.Sku,
    quantity: Int,
    unitPrice: BigDecimal,
    lineTotal: BigDecimal
)

case class Order(
    orderId: Order.OrderId,
    customerId: Customer.CustomerId,
    status: Option[OrderStatus],
    items: List[LineItem],
    subTotal: BigDecimal,
    discountAmount: BigDecimal,
    total: BigDecimal,
    createdAt: Option[Instant],
    updatedAt: Option[Instant]
)

object Order {
  opaque type OrderId = String

  object OrderId {
    def apply(value: String): OrderId = value
  }
  extension (input: OrderId) {
    def value: String = input
  }
}

object LineItem {
  opaque type Sku = String

  object Sku {
    def apply(value: String): Sku = value
  }
  extension (input: Sku) {
    def value: String = input
  }
}

package com.example.events

import java.time.Instant

case class OrderPricedEvent(
    eventId: String,
    orderId: String,
    customerId: String,
    subtotal: BigDecimal,
    discount: BigDecimal,
    total: BigDecimal,
    createdAt: Instant
)

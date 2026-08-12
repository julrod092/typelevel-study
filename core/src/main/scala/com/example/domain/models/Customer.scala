package com.example.domain.models

import com.example.domain.models.Order.OrderId

import java.time.Instant

enum CustomerTier {
  case BASIC, SILVER, GOLD
}

case class Customer(
    customerId: Customer.CustomerId,
    tier: CustomerTier,
    name: Option[String],
    createdAt: Instant
)

object Customer {
  opaque type CustomerId = String

  object CustomerId {
    def apply(value: String): CustomerId = value
  }

  extension (input: CustomerId) {
    def value: String = input
  }
}

package com.example.domain.models

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
}

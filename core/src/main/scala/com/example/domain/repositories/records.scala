package com.example.domain.repositories

sealed trait RepositoryRecord

case class CouponRecord(
    code: String,
    discountPercent: Int,
    minOrderAmount: BigDecimal,
    usageLimit: Int,
    usageCount: Int,
    expiresAt: String,
    stackableWithTiers: Boolean
) extends RepositoryRecord

case class CustomerRecord(
    customerId: String,
    tier: String,
    name: Option[String],
    createdAt: String
) extends RepositoryRecord

case class LineItemRecord(
    sku: String,
    quantity: Int,
    unitPrice: BigDecimal,
    lineTotal: BigDecimal
) extends RepositoryRecord

case class OrderRecord(
    orderId: String,
    customerId: String,
    status: String,
    items: List[LineItemRecord],
    subtotal: BigDecimal,
    discountAmount: BigDecimal,
    total: BigDecimal,
    couponCode: Option[String],
    createdAt: String,
    updatedAt: String
) extends RepositoryRecord

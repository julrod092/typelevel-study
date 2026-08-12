package com.example.domain.repositories

case class CouponRecord(
    code: String,
    discountAmount: BigDecimal,
    minOrderAmount: BigDecimal,
    usageLimit: Int,
    usageCount: Int,
    expiresAt: String,
    stackableWithTiers: Boolean
)

case class CustomerRecord(
    customerId: String,
    tier: String,
    name: Option[String],
    createdAt: String
)

case class LineItemRecord(
    sku: String,
    quantity: Int,
    unitPrice: BigDecimal,
    lineTotal: BigDecimal
)

case class OrderRecord(
    orderId: String,
    customerId: String,
    status: String,
    items: List[LineItemRecord],
    subtotal: BigDecimal,
    discountAmount: BigDecimal,
    total: BigDecimal,
    couponCode: String,
    createdAt: String,
    updatedAt: String
)

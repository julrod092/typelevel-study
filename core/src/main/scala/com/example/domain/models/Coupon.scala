package com.example.domain.models

import java.time.Instant

case class Coupon(
    code: Coupon.CouponCode,
    discountAmount: BigDecimal,
    minOrderAmount: BigDecimal,
    usageLimit: Int,
    usageCount: Int,
    expiresAt: Instant,
    stackableWithTiers: Boolean
)

object Coupon {
  opaque type CouponCode = String
}

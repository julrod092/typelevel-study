package com.example.domain.models

import java.time.Instant

case class Coupon(
    code: Coupon.CouponCode,
    discountPercent: Int,
    minOrderAmount: BigDecimal,
    usageLimit: Int,
    usageCount: Int,
    expiresAt: Instant,
    stackableWithTiers: Boolean
)

object Coupon {
  opaque type CouponCode = String

  object CouponCode {
    def apply(value: String): CouponCode = value
  }
  extension (input: CouponCode) {
    def value: String = input
  }
}

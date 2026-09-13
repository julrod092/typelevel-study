package com.example.domain.repositories

import com.example.domain.models.Coupon
import com.example.domain.models.Coupon.CouponCode

trait CouponsRepository[F[_]] {

  def couponByCouponCode(value: CouponCode): F[Option[CouponRecord]]

  def updateCouponUseByCoupon(value: CouponRecord): F[CouponRecord]
}

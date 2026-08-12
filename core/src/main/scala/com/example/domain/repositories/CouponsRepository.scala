package com.example.domain.repositories

import cats.data.EitherT
import cats.effect.Async
import com.example.domain.models.Coupon.CouponCode
import com.example.domain.models.PricingError

trait CouponsRepository[F[_] : Async] {

  def couponByCouponCode(value: CouponCode): EitherT[F, PricingError, Option[CouponRecord]]
}

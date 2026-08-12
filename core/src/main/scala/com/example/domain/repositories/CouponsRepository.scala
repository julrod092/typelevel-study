package com.example.domain.repositories

import cats.data.EitherT
import cats.effect.Async
import com.example.domain.models.Coupon.CouponCode

trait CouponsRepository[F[_] : Async] {

  def couponByCouponCode(value: CouponCode): EitherT[F, String, Option[CouponRecord]]
}

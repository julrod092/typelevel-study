package com.example.domain.repositories

import cats.data.EitherT
import cats.effect.Async
import com.example.domain.models.Coupon.CouponCode
import com.example.domain.models.DomainError

trait CouponsRepository[F[_]] {

  def couponByCouponCode(value: CouponCode): F[Option[CouponRecord]]
}

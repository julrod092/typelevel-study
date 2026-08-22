package com.example.support

import cats.effect.IO
import cats.effect.kernel.{Clock, Ref}
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import com.example.domain.models.{Coupon, Customer}
import com.example.domain.repositories.*
import com.example.infrastructure.configuration.PricingEnvironment

final case class RepositoryCalls(
    customerIds: List[Customer.CustomerId],
    couponCodes: List[Coupon.CouponCode],
    savedOrders: List[OrderRecord]
)

final case class PricingEnvironmentStub(
    environment: PricingEnvironment[IO],
    calls: Ref[IO, RepositoryCalls]
)

object PricingEnvironmentStub {

  def create(
      customerResult: Option[CustomerRecord],
      couponResult: Option[CouponRecord] = None,
      saveError: Option[Throwable] = None
  ): IO[PricingEnvironmentStub] =
    Ref
      .of[IO, RepositoryCalls](RepositoryCalls(Nil, Nil, Nil))
      .map { calls =>
        val customers = new CustomersRepository[IO] {
          override def customerByCustomerId(
              value: Customer.CustomerId
          ): IO[Option[CustomerRecord]] =
            calls
              .update(state => state.copy(customerIds = state.customerIds :+ value))
              .as(customerResult)
        }
        val coupons = new CouponsRepository[IO] {
          override def couponByCouponCode(
              value: Coupon.CouponCode
          ): IO[Option[CouponRecord]] =
            calls
              .update(state => state.copy(couponCodes = state.couponCodes :+ value))
              .as(couponResult)
        }
        val orders = new OrdersRepository[IO] {
          override def savePricedOrder(value: OrderRecord): IO[OrderRecord] =
            calls.update(state => state.copy(savedOrders = state.savedOrders :+ value)) *>
              saveError.fold(IO.pure(value))(IO.raiseError)
        }

        PricingEnvironmentStub(
          environment = PricingEnvironment(customers, coupons, orders, Clock[IO], UUIDGen[IO]),
          calls = calls
        )
      }
}

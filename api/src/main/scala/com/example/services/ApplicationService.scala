package com.example.services

import cats.Monad
import cats.data.{EitherT, Kleisli, ValidatedNec}
import cats.syntax.all.*
import com.example.domain.models.{Coupon, Customer, CustomerOrder, DomainError, Order}
import com.example.domain.repositories.{CouponRecord, CustomerRecord}
import com.example.domain.services.{OrderPriceService, ValidationService}
import com.example.infrastructure.errors.ErrorHandler
import com.example.infrastructure.transformers.ApiTransformer.given
import io.scalaland.chimney.dsl.*
import pricing.*
import pricing.PriceAPIOperation.OrderPricingError

object ApplicationService extends BaseService {

  private def executeDB[F[_]: Monad, A, T, X](
      value: A
  )(f: A => F[Option[T]]): Response[F, X] = {
    EitherT
      .fromOptionF[F, OrderPricingError, T](
        f(value),
        OrderPricingError.notFoundError(
          NotFoundError("NOT_FOUND".some, s"Can not find value $value".some)
        )
      )
      .map(_.transformInto[X])
  }

  private def executeValidation[F[_]: Monad](value: CustomerOrder)(
      f: CustomerOrder => ValidatedNec[DomainError, CustomerOrder]
  ): Response[F, CustomerOrder] =
    EitherT.fromEither[F](f(value).toEither.leftMap(ErrorHandler.handleDomainErrors))

  private def executePricing[F[_]: Monad](
      order: CustomerOrder,
      customer: Customer,
      coupon: Option[Coupon]
  )(
      f: (CustomerOrder, Customer, Option[Coupon]) => ValidatedNec[DomainError, Order]
  ): Response[F, Order] =
    EitherT.fromEither[F](
      f(order, customer, coupon).toEither.leftMap(ErrorHandler.handleDomainErrors)
    )

  def createOrder[F[_]: Monad](
      dto: OrderPriceDTO
  ): Environment[F, OrderPricedDTO] = Kleisli { env =>
    val domainObj = dto.transformInto[CustomerOrder]
    for {
      validation <- executeValidation(domainObj)(ValidationService.validate)
      customer <- executeDB[F, Customer.CustomerId, CustomerRecord, Customer](
        domainObj.customerId
      )(env.customers.customerByCustomerId)
      coupon <- domainObj.couponCode.fold[Response[F, Option[Coupon]]](
        EitherT.rightT[F, OrderPricingError](None)
      )(value =>
        executeDB[F, Coupon.CouponCode, CouponRecord, Option[Coupon]](value)(
          env.coupons.couponByCouponCode
        )
      )
      result <- executePricing(validation, customer, coupon)(OrderPriceService.createOrder)
    } yield result.transformInto[OrderPricedDTO]
  }
}

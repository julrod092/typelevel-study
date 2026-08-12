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

  private def executeDB[F[_]: Monad, T, X](value: String)(f: String => F[T]): Response[F, X] =
    f(value).map2 {
      case Some(record) => EitherT.rightT[F, OrderPricingError](record.transformInto[X])
      case None         =>
        EitherT.leftT[F, T](
          OrderPricingError.notFoundError(
            NotFoundError("NOT_FOUND".some, s"Can not find value $value".some)
          )
        )
    }

  private def executeValidation[F[_]: Monad](value: CustomerOrder)(
      f: CustomerOrder => ValidatedNec[DomainError, CustomerOrder]
  ): Response[F, CustomerOrder] =
    EitherT.fromEither[F](f(value).toEither.leftMap(ErrorHandler.handleDomainErrors))

  private def executePricing[F[_]: Monad](order: CustomerOrder, coupon: Option[Coupon])(
      f: (CustomerOrder, Option[Coupon]) => ValidatedNec[DomainError, Order]
  ): Response[F, Order] =
    EitherT.fromEither[F](f(order, coupon).toEither.leftMap(ErrorHandler.handleDomainErrors))

  def createOrder[F[_]: Monad](
      dto: OrderPriceDTO
  ): Environment[F, OrderPricedDTO] = Kleisli { env =>
    val domainObj = dto.transformInto[CustomerOrder]
    for {
      validation <- executeValidation(domainObj)(ValidationService.validate)
      customer <- executeDB[F, Option[CustomerRecord], Customer](domainObj.customerId.value)(
        env.customers.customerByCustomerId
      )
      coupon <- domainObj.couponCode.fold[Response[F, Option[Coupon]]](
        EitherT.rightT[F, OrderPricingError](None)
      )(value =>
        executeDB[F, Option[CouponRecord], Option[Coupon]](value.value)(
          env.coupons.couponByCouponCode
        )
      )
      result <- executePricing(validation, coupon)(OrderPriceService.createOrder)
    } yield null.transformInto[OrderPricedDTO]
  }
}

package com.example.services

import cats.Monad
import cats.data.{EitherT, Kleisli, ValidatedNec}
import cats.effect.{Clock, Sync}
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import com.example.domain.models.{Coupon, Customer, CustomerOrder, DomainError, Order}
import com.example.domain.repositories.{CouponRecord, CustomerRecord}
import com.example.domain.services.{OrderPriceService, ValidationService}
import com.example.infrastructure.errors.ErrorHandler
import com.example.infrastructure.transformers.ApiTransformer.given
import com.example.infrastructure.transformers.RecordTransformer.given
import io.scalaland.chimney.dsl.*
import pricing.*
import pricing.PriceAPIOperation.OrderPricingError
import io.scalaland.chimney.Transformer
import com.example.domain.repositories.OrderRecord

import java.time.Instant

object ApplicationService extends BaseService {

  private def executeDB[F[_]: Monad, A, T, X](
      value: A
  )(f: A => F[Option[T]])(using transformer: Transformer[T, X]): Response[F, X] =
    EitherT
      .fromOptionF[F, OrderPricingError, T](
        f(value),
        OrderPricingError.notFoundError(
          NotFoundError("NOT_FOUND".some, s"Can not find value $value".some)
        )
      )
      .map(transformer.transform)

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

  def createOrder[F[_]: Sync](
      dto: OrderPriceDTO
  ): Environment[F, OrderPricedDTO] = Kleisli { env =>
    val domainObj = dto.transformInto[CustomerOrder]
    for {
      validation <- executeValidation(domainObj)(ValidationService.validate)
      customer <- executeDB[F, Customer.CustomerId, CustomerRecord, Customer](
        domainObj.customerId
      )(env.customers.customerByCustomerId)
      coupon <- domainObj.couponCode.traverse { value =>
        executeDB[F, Coupon.CouponCode, CouponRecord, Coupon](value)(
          env.coupons.couponByCouponCode
        )
      }
      now <- EitherT.liftF[F, OrderPricingError, Instant](Clock[F].realTimeInstant)
      orderId <- EitherT.liftF[F, OrderPricingError, Order.OrderId](
        UUIDGen[F].randomUUID.map(uuid => Order.OrderId(uuid.toString))
      )
      result <- executePricing(validation, customer, coupon) { (order, customer, coupon) =>
        OrderPriceService.createOrder(order, customer, coupon, orderId, now)
      }
      savedResult <- EitherT
        .liftF[F, OrderPricingError, OrderRecord](
          env.orders.savePricedOrder(result.transformInto[OrderRecord])
        )
        .leftMap { case error =>
          pricing.PriceAPIGen.OrderPricingError.internalServerError(
            InternalServerError("500".some, "Error storing order, please try again later.".some)
          )
        }
    } yield result.transformInto[OrderPricedDTO]
  }
}

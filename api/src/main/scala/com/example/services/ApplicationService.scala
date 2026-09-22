package com.example.services

import cats.{Monad, MonadThrow}
import cats.data.{EitherT, Kleisli}
import cats.effect.Async
import cats.syntax.all.*
import com.example.domain.models.{Coupon, Customer, CustomerOrder, Order}
import com.example.domain.repositories.{CouponRecord, CustomerRecord, OrderRecord}
import com.example.domain.services.{OrderPriceService, ValidationService}
import com.example.infrastructure.configuration.PricingEnvironment
import com.example.infrastructure.errors.ErrorHandler
import com.example.infrastructure.transformers.ApiTransformer.given
import com.example.infrastructure.transformers.RecordTransformer.given
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import pricing.*
import pricing.PriceAPIOperation.OrderPricingError

object ApplicationService extends BaseService {

  private def executeOptionDB[F[_]: Monad, A, T, X](
      value: A
  )(
      f: PricingEnvironment[F] => A => F[Option[T]]
  )(using transformer: Transformer[T, X]): Program[F, X] = Kleisli { env =>
    EitherT
      .fromOptionF[F, OrderPricingError, T](
        f(env)(value),
        OrderPricingError.notFoundError(
          NotFoundError("NOT_FOUND".some, s"Can not find value $value".some)
        )
      )
      .map(transformer.transform)
  }

  private def executeMandatoryDB[F[_]: MonadThrow, A, T, X](
      value: A
  )(f: PricingEnvironment[F] => A => F[T])(using
      transformer: Transformer[T, X]
  ): Program[F, X] = Kleisli { env =>
    f(env)(value)
      .attemptT
      .leftMap[OrderPricingError](e  =>
        println(e.getMessage)
        OrderPricingError.internalServerError(
          InternalServerError("UPSERT_ERROR".some, "Error storing order, try again later.".some)
        )
      )
      .map(transformer.transform)
  }

  private def priceOrder[F[_]: Monad](
      order: CustomerOrder,
      customer: Customer,
      coupon: Option[Coupon]
  ): Program[F, Order] =
    Kleisli { env =>
      EitherT(
        for {
          clock <- env.clock.realTimeInstant
          uuid <- env.idGenerator.randomUUID.map(_.toString)
        } yield OrderPriceService
          .createOrder(order, customer, coupon, Order.OrderId(uuid), clock)
          .toEither
          .leftMap(ErrorHandler.handleDomainErrors)
      )
    }

  private def upsert[F[_]: MonadThrow](order: Order, coupon: Option[Coupon]): Program[F, Order] =
    for {
      orderSaveResult <- executeMandatoryDB[F, OrderRecord, OrderRecord, Order](
        order.transformInto[OrderRecord]
      )(_.orders.savePricedOrder)
      couponUsageUpdate = coupon.map(c => c.copy(usageCount = c.usageCount + 1))
      _ <- couponUsageUpdate.traverse(c =>
        executeMandatoryDB[F, CouponRecord, CouponRecord, Coupon](c.transformInto[CouponRecord])(
          _.coupons.updateCouponUseByCoupon
        )
      )
    } yield orderSaveResult

  def createOrder[F[_]: Async](
      dto: OrderPriceDTO
  ): Program[F, OrderPricedDTO] = {
    for {
      domain <- Kleisli(_ => EitherT.pure[F, OrderPricingError](dto.transformInto[CustomerOrder]))
      validation <- Kleisli { _ =>
        EitherT.fromEither[F](
          ValidationService.validate(domain).toEither.leftMap(ErrorHandler.handleDomainErrors)
        )
      }
      customer <- executeOptionDB[F, Customer.CustomerId, CustomerRecord, Customer](
        validation.customerId
      )(_.customers.customerByCustomerId)
      coupon <- validation.couponCode.traverse(
        executeOptionDB[F, Coupon.CouponCode, CouponRecord, Coupon](_)(_.coupons.couponByCouponCode)
      )
      result <- priceOrder[F](validation, customer, coupon)
      _ <- upsert[F](result, coupon)
    } yield result.transformInto[OrderPricedDTO]
  }
}

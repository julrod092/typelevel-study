package com.example.services

import cats.Monad
import cats.data.{EitherT, Kleisli}
import cats.effect.{Clock, Sync}
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import com.example.domain.models.{Coupon, Customer, CustomerOrder, DomainError, Order}
import com.example.domain.repositories.{CouponRecord, CustomerRecord, OrderRecord}
import com.example.domain.services.{OrderPriceService, ValidationService}
import com.example.infrastructure.configuration.PricingEnvironment
import com.example.infrastructure.errors.ErrorHandler
import com.example.infrastructure.transformers.ApiTransformer.given
import com.example.infrastructure.transformers.RecordTransformer.given
import io.scalaland.chimney.dsl.*
import pricing.*
import pricing.PriceAPIOperation.OrderPricingError
import io.scalaland.chimney.Transformer

import java.time.Instant

object ApplicationService extends BaseService {

  private def executeDB[F[_]: Monad, A, T, X](
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

  private def validate[F[_]: Monad](value: CustomerOrder): Program[F, CustomerOrder] =
    Kleisli { _ =>
      EitherT.fromEither[F](
        ValidationService.validate(value).toEither.leftMap(ErrorHandler.handleDomainErrors)
      )
    }

  private def price[F[_]: Monad](
      order: CustomerOrder,
      customer: Customer,
      coupon: Option[Coupon]
  ): Program[F, Order] =
    Kleisli { env =>
      EitherT(for {
        clock <- env.clock.realTimeInstant
        uuid <- env.idGenerator.randomUUID.map(_.toString)
      } yield {
        OrderPriceService
          .createOrder(order, customer, coupon, Order.OrderId(uuid), clock)
          .toEither
          .leftMap(ErrorHandler.handleDomainErrors)
      })
    }

  private def save[F[_]: Sync](order: Order): Program[F, OrderRecord] =
    Kleisli { env =>
      EitherT(
        env.orders
          .savePricedOrder(order.transformInto[OrderRecord])
          .attempt
          .map(
            _.leftMap[OrderPricingError](_ =>
              OrderPricingError.internalServerError(
                InternalServerError(
                  "500".some,
                  "Error storing order, please try again later.".some
                )
              )
            )
          )
      )
    }

  def createOrder[F[_]: Sync](
      dto: OrderPriceDTO
  ): Program[F, OrderPricedDTO] = {
    val domainObj = dto.transformInto[CustomerOrder]
    for {
      validation <- validate[F](domainObj)
      customer <- executeDB[F, Customer.CustomerId, CustomerRecord, Customer](
        validation.customerId
      )(_.customers.customerByCustomerId)
      coupon <- validation.couponCode.traverse(
        executeDB[F, Coupon.CouponCode, CouponRecord, Coupon](_)(_.coupons.couponByCouponCode)
      )
      result <- price[F](validation, customer, coupon)
      _ <- save[F](result)
    } yield result.transformInto[OrderPricedDTO]
  }
}

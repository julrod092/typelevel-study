package com.example.services

import pricing.*
import pricing.DomainError
import io.scalaland.chimney.dsl.*
import com.example.infrastructure.transformers.ApiTransformer.*
import com.example.infrastructure.transformers.ApiTransformer.given
import com.example.domain.models.CustomerOrder
import cats.data.EitherT
import com.example.domain.services.ValidationService
import cats.instances.all._
import cats.syntax.all._
import com.example.domain.services.OrderPriceService
import cats.effect.Async
import com.example.domain.models.PricingError
import cats.data.NonEmptyChain
import com.example.domain.models.PricingError.*
import pricing.PriceAPIOperation.OrderPricingError
import com.example.domain.models.{Coupon, LineItem}
import io.scalaland.chimney.Transformer

object ApplicationService {

  private def domainErrorHandler(
      errors: NonEmptyChain[PricingError] | PricingError
  ): OrderPricingError = {
    errors match {
      case validationErrors: NonEmptyChain[_] =>
        val tranformer: List[DomainError] = validationErrors.foldLeft(List.empty) { (acc, tar) =>
          val tranformToAppError = tar match {
            case error: EmptyCustomerId =>
              DomainError.generalError(GeneralValidationError(error.errorCode, error.message))
            case error: EmptyLineItemList =>
              DomainError.generalError(GeneralValidationError(error.errorCode, error.message))
            case error: EmptyItemSku =>
              DomainError.generalError(GeneralValidationError(error.errorCode, error.message))
            case error: EmptyCouponCode =>
              DomainError.generalError(GeneralValidationError(error.errorCode, error.message))
            case error: UnknownSku =>
              DomainError.itemError(
                ItemValidationError(error.errorCode, error.message, error.sku.value.some)
              )
            case error: InvalidItemQuantity =>
              DomainError.itemError(
                ItemValidationError(error.errorCode, error.message, error.sku.value.some)
              )
            case error: CouponLimitReached =>
              DomainError.couponError(
                CouponValidationError(error.errorCode, error.message, error.code.value.some)
              )
            case error: CouponNotStackable =>
              DomainError.couponError(
                CouponValidationError(error.errorCode, error.message, error.code.value.some)
              )
            case error: CouponExpirationError =>
              DomainError.couponError(
                CouponValidationError(error.errorCode, error.message, error.code.value.some)
              )
            case error: CouponUnderExpectedAmount =>
              DomainError.couponError(
                CouponValidationError(error.errorCode, error.message, error.code.value.some)
              )
          }
          acc ++ List(tranformToAppError)
        }
        OrderPricingError.validationErrors(ValidationErrors(tranformer))

      case err: CustomerNotFound =>
        OrderPricingError.notFoundError(NotFoundError(err.errorCode.some, err.message.some))
      case err: CouponNotFound =>
        OrderPricingError.notFoundError(NotFoundError(err.errorCode.some, err.message.some))
      case err: OrderNotSaved =>
        OrderPricingError.notFoundError(NotFoundError(err.errorCode.some, err.message.some))
      case err: OrderNotFound =>
        OrderPricingError.notFoundError(NotFoundError(err.errorCode.some, err.message.some))
    }
  }

  def createOrder[F[_]: Async](
      dto: OrderPriceDTO
  ): EitherT[F, OrderPricingError, OrderPricedDTO] = {
    val toDomain: CustomerOrder = dto.transformInto[CustomerOrder]
    (for {
      validatedCustomerOrder <- EitherT.fromEither[F](
        ValidationService.validate(toDomain).toEither
      )
      orderCreation <- EitherT.fromEither[F](
        OrderPriceService.createOrder(validatedCustomerOrder, None).toEither
      )
    } yield orderCreation.transformInto[OrderPricedDTO])
      .leftMap(domainErrorHandler)
  }
}

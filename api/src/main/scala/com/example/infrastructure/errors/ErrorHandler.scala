package com.example.infrastructure.errors

import cats.data.NonEmptyChain
import com.example.domain.models.Customer.CustomerId
import com.example.domain.models.DomainError.*
import com.example.domain.models.{Coupon, DomainError, Order}
import pricing.PriceAPIOperation.OrderPricingError
import pricing.*
import cats.syntax.all.*

object ErrorHandler {

  def handleDomainErrors(errors: NonEmptyChain[DomainError]): OrderPricingError = {
    val pricingErrors: List[ValidationError] = errors.foldLeft(List.empty) { (acc, tar) =>
      val transformToAppError = tar match {
        case error: EmptyCustomerId =>
          ValidationError.generalError(GeneralValidationError(error.errorCode, error.message))
        case error: EmptyLineItemList =>
          ValidationError.generalError(GeneralValidationError(error.errorCode, error.message))
        case error: EmptyItemSku =>
          ValidationError.generalError(GeneralValidationError(error.errorCode, error.message))
        case error: EmptyCouponCode =>
          ValidationError.generalError(GeneralValidationError(error.errorCode, error.message))
        case error: UnknownSku =>
          ValidationError.itemError(
            ItemValidationError(error.errorCode, error.message, error.sku.value.some)
          )
        case error: InvalidItemQuantity =>
          ValidationError.itemError(
            ItemValidationError(error.errorCode, error.message, error.sku.value.some)
          )
        case error: CouponLimitReached =>
          ValidationError.couponError(
            CouponValidationError(error.errorCode, error.message, error.code.value.some)
          )
        case error: CouponNotStackable =>
          ValidationError.couponError(
            CouponValidationError(error.errorCode, error.message, error.code.value.some)
          )
        case error: CouponExpirationError =>
          ValidationError.couponError(
            CouponValidationError(error.errorCode, error.message, error.code.value.some)
          )
        case error: CouponUnderExpectedAmount =>
          ValidationError.couponError(
            CouponValidationError(error.errorCode, error.message, error.code.value.some)
          )
      }
      acc ++ List(transformToAppError)
    }
    OrderPricingError.validationErrors(ValidationErrors(pricingErrors))
  }

}

package com.example.services

import pricing.*
import pricing.PriceAPIGen.OrderPricingError
import io.scalaland.chimney.dsl.*
import com.example.infrastructure.transformers.ApiTransformer.*
import com.example.domain.models.CustomerOrder
import cats.data.EitherT
import com.example.domain.services.ValidationService
import cats.instances.all._
import cats.syntax.all._
import com.example.domain.services.OrderPriceService
import cats.effect.Async
import com.example.domain.models.PrincingError
import cats.data.NonEmptyChain
import com.example.domain.models.PrincingError.*

object ApplicationService {

  /*
  case EmptyCustomerId(message: String) extends PrincingError("EMPTY_CUSTOMER_ID")
  case EmptyItemSku(message: String) extends PrincingError("EMPTY_ITEM_SKU")
  case EmptyLineItemList(message: String) extends PrincingError("EMPTY_ITEM_LIST")
  case EmptyCouponCode(message: String) extends PrincingError("EMPTY_COUPON_CODE")
  case UnknownSku(message: String, sku: LineItem.Sku) extends PrincingError("UNKNOW_SKU")
  case InvalidItemQuantity(message: String, sku: LineItem.Sku)
      extends PrincingError("INVALID_ITEM_QUANITY")
  case CouponLimitReached(message: String, code: Coupon.CouponCode)
      extends PrincingError("COUPON_LIMIT_REACHED")
  case CouponNotStackable(message: String, code: Coupon.CouponCode)
      extends PrincingError("COUPON_NOT_STACKABLE")
  case CouponExpirationError(message: String, code: Coupon.CouponCode)
      extends PrincingError("COUPON_EXPIRED")
  case CouponUnderExpectedAmount(message: String, code: Coupon.CouponCode),bc
      extends PrincingError("COUPON_UNDER_EXPECTED_AMOUNT")
  case CouponInvalidDiscountPercentage(message: String, code: Coupon.CouponCode)
      extends PrincingError("INVALID_DISCOUNT_PERCENTAGE")
  case InvalidOrderAmountAggregation(message: String, orderId: Order.OrderId)
      extends PrincingError("INVALID_ORDER_AMOUNT_AGGREGATION")

   */
  private def errorHandler(error: NonEmptyChain[PrincingError]): OrderPricingError =
    error.foldLeft(List.empty[OrderPricingError]) { (acc, tar) =>
      val errorType = tar match {
        case error: (EmptyCustomerId | EmptyItemSku | EmptyLineItemList) => GeneralValidationError(error.errorId, error.message)
      }
      acc
    }

  def createOrder[F[_]: Async](dto: OrderPriceDTO): EitherT[F, OrderPricingError, OrderPricedDTO] = {
    val toDomain: CustomerOrder = dto.transformInto[CustomerOrder]
    for {
      validatedCustomerOrder <- EitherT.fromEither[F](ValidationService.validate(toDomain).toEither)
      orderCreation <- EitherT.fromEither[F](OrderPriceService.createOrder(validatedCustomerOrder, None).toEither)
    } yield orderCreation.transformInto[OrderPricedDTO]
  }
}

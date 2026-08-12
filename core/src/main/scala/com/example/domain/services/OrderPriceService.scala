package com.example.domain.services

import com.example.domain.models.CustomerOrder
import com.example.domain.models.Coupon
import cats.data.ValidatedNec
import com.example.domain.models.PrincingError
import com.example.domain.models.Order
import cats.syntax.all.*
import cats.instances.all.*
import com.example.domain.models.LineItem
import java.util.UUID
import com.example.domain.models.CustomerLineItem
import com.example.domain.models.OrderStatus
import java.time.Instant
import cats.instances.tuple

object OrderPriceService {

  private val lineItemsValueCache: Map[LineItem.Sku, BigDecimal] = Map(
    LineItem.Sku("SKU-001") -> 19.99,
    LineItem.Sku("SKU-045") -> 49.99,
    LineItem.Sku("SKU-062") -> 32.99,
    LineItem.Sku("SKU-430") -> 109.00
  )

  private def priceLineItem(
      lineItem: CustomerLineItem
  ): ValidatedNec[PrincingError, LineItem] =
    lineItemsValueCache
      .get(lineItem.sku)
      .fold(PrincingError.UnknownSku("Item sku not found", lineItem.sku).invalidNec) { valueAmount =>
        LineItem(
          sku = lineItem.sku,
          quantity = lineItem.quantity,
          unitPrice = valueAmount,
          lineTotal = valueAmount * lineItem.quantity
        ).validNec
      }

  private def validateCoupon(
      coupon: Coupon,
      subTotal: BigDecimal
  ): ValidatedNec[PrincingError, Coupon] = {
    val validateExpiration: ValidatedNec[PrincingError, Instant] =
      if (coupon.expiresAt.isAfter(Instant.now())) coupon.expiresAt.validNec
      else PrincingError.CouponExpirationError("Coupon Expired", coupon.code).invalidNec

    val validateUsage: ValidatedNec[PrincingError, Boolean] =
      if (coupon.usageCount <= coupon.usageLimit) true.validNec
      else PrincingError.CouponLimitReached("Coupon uses passed its limit", coupon.code).invalidNec

    val isStackableWithTier: ValidatedNec[PrincingError, Boolean] =
      if (coupon.stackableWithTiers) coupon.stackableWithTiers.validNec
      else
        PrincingError
          .CouponNotStackable("Coupon not apply for customer tier", coupon.code)
          .invalidNec

    val isMinAmount: ValidatedNec[PrincingError, Boolean] =
      if (subTotal >= coupon.minOrderAmount) true.validNec
      else
        PrincingError
          .CouponUnderExpectedAmount(
            "Order does not meet expected amount for discount",
            coupon.code
          )
          .invalidNec

    val isValidDiscountPercent: ValidatedNec[PrincingError, Int] =
      if (coupon.discountPercent > 0 && coupon.discountPercent < 100)
        coupon.discountPercent.validNec
      else
        PrincingError
          .CouponInvalidDiscountPercentage(
            "Coupon discount percentage is an invalid value",
            coupon.code
          )
          .invalidNec

    (
      validateExpiration,
      validateUsage,
      isStackableWithTier,
      isMinAmount
    ).mapN { (_, _, _, _) => coupon }
  }

  def createOrder(
      customerOrder: CustomerOrder,
      coupon: Option[Coupon]
  ): ValidatedNec[PrincingError, Order] = {

    customerOrder.items
      .traverse(priceLineItem)
      .andThen { items =>
        val subtotal: BigDecimal = items.map(_.lineTotal).sum
        val validatedCoupon: Option[Coupon] =
          coupon.flatMap(value => validateCoupon(value, subtotal).toOption)
        val discountAmount: BigDecimal =
          validatedCoupon.map(value => subtotal * (value.discountPercent / 100)).getOrElse(0)
        Order(
          orderId = Order.OrderId(UUID.randomUUID().toString()),
          customerId = customerOrder.customerId,
          status = OrderStatus.InProgress,
          items = items,
          subtotal = subtotal,
          discountAmount = discountAmount,
          total = subtotal - discountAmount,
          couponCode = validatedCoupon.map(_.code),
          createdAt = Instant.now(),
          updatedAt = Instant.now()
        ).validNec
      }
  }
}

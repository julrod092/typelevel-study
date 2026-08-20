package com.example.domain.services

import com.example.domain.models.{Coupon, Customer, CustomerLineItem, CustomerOrder, DomainError, LineItem, Order, OrderStatus}
import cats.data.ValidatedNec
import cats.syntax.all.*
import cats.instances.all.*

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
  ): ValidatedNec[DomainError, LineItem] =
    lineItemsValueCache
      .get(lineItem.sku)
      .fold(DomainError.UnknownSku("Item sku not found", lineItem.sku).invalidNec) { valueAmount =>
        LineItem(
          sku = lineItem.sku,
          quantity = lineItem.quantity,
          unitPrice = valueAmount,
          lineTotal = valueAmount * lineItem.quantity
        ).validNec
      }

  private def validateCoupon(
      coupon: Coupon,
      customer: Customer,
      subTotal: BigDecimal,
      now: Instant
  ): ValidatedNec[DomainError, Coupon] = {
    val validateExpiration: ValidatedNec[DomainError, Instant] =
      if (coupon.expiresAt.isAfter(now)) coupon.expiresAt.validNec
      else DomainError.CouponExpirationError("Coupon Expired", coupon.code).invalidNec

    val validateUsage: ValidatedNec[DomainError, Boolean] =
      if (coupon.usageCount < coupon.usageLimit) true.validNec
      else DomainError.CouponLimitReached("Coupon uses passed its limit", coupon.code).invalidNec

    val isStackableWithTier: ValidatedNec[DomainError, Boolean] =
      if (coupon.stackableWithTiers && customer.tier.isApplicable) coupon.stackableWithTiers.validNec
      else
        DomainError
          .CouponNotStackable("Coupon not apply for customer tier", coupon.code)
          .invalidNec

    val isMinAmount: ValidatedNec[DomainError, Boolean] =
      if (subTotal >= coupon.minOrderAmount) true.validNec
      else
        DomainError
          .CouponUnderExpectedAmount(
            "Order does not meet expected amount for discount",
            coupon.code
          )
          .invalidNec

    val isValidDiscountPercent: ValidatedNec[DomainError, Int] =
      if (coupon.discountPercent > 0 && coupon.discountPercent < 100)
        coupon.discountPercent.validNec
      else
        DomainError
          .CouponInvalidDiscountPercentage(
            "Coupon discount percentage is an invalid value",
            coupon.code
          )
          .invalidNec

    (
      validateExpiration,
      validateUsage,
      isStackableWithTier,
      isMinAmount,
      isValidDiscountPercent
    ).mapN { (_, _, _, _, _) => coupon }
  }

  def createOrder(
      customerOrder: CustomerOrder,
      customer: Customer,
      coupon: Option[Coupon],
      orderId: Order.OrderId,
      now: Instant
  ): ValidatedNec[DomainError, Order] = {

    customerOrder.items
      .traverse(priceLineItem)
      .andThen { items =>
        val subtotal: BigDecimal = items.map(_.lineTotal).sum

        coupon.traverse(validateCoupon(_, customer, subtotal, now)).map { validatedCoupon =>
          val discountAmount: BigDecimal = validatedCoupon
            .map(value => subtotal * BigDecimal(value.discountPercent) / 100)
            .getOrElse(0)

          Order(
            orderId = orderId,
            customerId = customerOrder.customerId,
            status = OrderStatus.InProgress,
            items = items,
            subtotal = subtotal,
            discountAmount = discountAmount,
            total = subtotal - discountAmount,
            couponCode = validatedCoupon.map(_.code),
            createdAt = now,
            updatedAt = now
          )
        }
      }
  }
}

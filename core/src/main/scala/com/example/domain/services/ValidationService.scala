package com.example.domain.services

import cats.data.EitherT
import com.example.domain.models.CustomerOrder
import com.example.domain.models.PricingError
import com.example.domain.models.Order
import cats.effect.Async
import cats.data.ValidatedNec
import com.example.domain.models.Customer
import cats.syntax.all.*
import cats.instances.all.*
import com.example.domain.models.LineItem
import com.example.domain.models.CustomerLineItem
import com.example.domain.models.Coupon

object ValidationService {

  private def validateCustomerId(
      customerId: Customer.CustomerId
  ): ValidatedNec[PricingError, Customer.CustomerId] = {
    if (customerId.value.nonEmpty) customerId.validNec
    else PricingError.EmptyCustomerId("Empty Customer ID").invalidNec
  }

  private def validateLineItem(
      lineItem: CustomerLineItem
  ): ValidatedNec[PricingError, CustomerLineItem] = {

    val validateSku: ValidatedNec[PricingError, LineItem.Sku] =
      if (lineItem.sku.value.nonEmpty) lineItem.sku.validNec
      else PricingError.EmptyItemSku("Empty item sku").invalidNec
    val validateQuantity: ValidatedNec[PricingError, Int] =
      if (lineItem.quantity > 0) lineItem.quantity.validNec
      else PricingError.InvalidItemQuantity("Item quantity is negative", lineItem.sku).invalidNec

    (
      validateSku,
      validateQuantity
    ).mapN(CustomerLineItem.apply)
  }

  private def validateLineItems(
      lineItems: List[CustomerLineItem]
  ): ValidatedNec[PricingError, List[CustomerLineItem]] = {
    if (lineItems.nonEmpty) lineItems.traverse(validateLineItem)
    else PricingError.EmptyLineItemList("List of items is empty").invalidNec
  }

  private def validateCouponCode(
      couponCode: Option[Coupon.CouponCode]
  ): ValidatedNec[PricingError, Option[Coupon.CouponCode]] =
    couponCode.fold(couponCode.validNec) { code =>
      if (code.value.nonEmpty) Some(code).validNec
      else PricingError.EmptyCouponCode("Coupon code set but empty").invalidNec
    }

  def validate(customerOrder: CustomerOrder): ValidatedNec[PricingError, CustomerOrder] =
    (
      validateCustomerId(customerOrder.customerId),
      validateLineItems(customerOrder.items),
      validateCouponCode(customerOrder.couponCode)
    ).mapN(CustomerOrder.apply)
}

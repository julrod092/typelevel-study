package com.example.domain.services

import cats.data.EitherT
import com.example.domain.models.CustomerOrder
import com.example.domain.models.DomainError
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
  ): ValidatedNec[DomainError, Customer.CustomerId] = {
    if (customerId.value.nonEmpty) customerId.validNec
    else DomainError.EmptyCustomerId("Empty Customer ID").invalidNec
  }

  private def validateLineItem(
      lineItem: CustomerLineItem
  ): ValidatedNec[DomainError, CustomerLineItem] = {

    val validateSku: ValidatedNec[DomainError, LineItem.Sku] =
      if (lineItem.sku.value.nonEmpty) lineItem.sku.validNec
      else DomainError.EmptyItemSku("Empty item sku").invalidNec
    val validateQuantity: ValidatedNec[DomainError, Int] =
      if (lineItem.quantity > 0) lineItem.quantity.validNec
      else DomainError.InvalidItemQuantity("Item quantity is negative", lineItem.sku).invalidNec

    (
      validateSku,
      validateQuantity
    ).mapN(CustomerLineItem.apply)
  }

  private def validateLineItems(
      lineItems: List[CustomerLineItem]
  ): ValidatedNec[DomainError, List[CustomerLineItem]] = {
    if (lineItems.nonEmpty) lineItems.traverse(validateLineItem)
    else DomainError.EmptyLineItemList("List of items is empty").invalidNec
  }

  private def validateCouponCode(
      couponCode: Option[Coupon.CouponCode]
  ): ValidatedNec[DomainError, Option[Coupon.CouponCode]] =
    couponCode.fold(couponCode.validNec) { code =>
      if (code.value.nonEmpty) Some(code).validNec
      else DomainError.EmptyCouponCode("Coupon code set but empty").invalidNec
    }

  def validate(customerOrder: CustomerOrder): ValidatedNec[DomainError, CustomerOrder] =
    (
      validateCustomerId(customerOrder.customerId),
      validateLineItems(customerOrder.items),
      validateCouponCode(customerOrder.couponCode)
    ).mapN(CustomerOrder.apply)
}

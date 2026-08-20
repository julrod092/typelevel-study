package com.example.domain.services

import cats.syntax.all.*
import com.example.domain.models.*
import com.example.generators.DomainGenerators
import com.example.generators.DomainGenerators.given
import monocle.syntax.all.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object ValidationServiceSuite extends SimpleIOSuite with Checkers {

  test(
    "A customer order, once every required field and line item is valid, is returned without changing request data"
  ) {
    forall(DomainGenerators.validCustomerOrder) { order =>
      expect(ValidationService.validate(order).toOption.contains(order))
    }
  }

  test(
    "When a customer submits malformed identity, line-item, and coupon data together, every problem is returned in request order"
  ) {
    forall(DomainGenerators.validCustomerOrder) { order =>
      val firstInvalidItem = order.items.head
        .focus(_.sku)
        .replace(LineItem.Sku(""))
        .focus(_.quantity)
        .replace(0)
      val secondInvalidItem = order.items.head
        .focus(_.quantity)
        .replace(-1)
      val invalidOrder = order
        .focus(_.customerId)
        .replace(Customer.CustomerId(""))
        .focus(_.items)
        .replace(List(firstInvalidItem, secondInvalidItem))
        .focus(_.couponCode)
        .replace(Some(Coupon.CouponCode("")))

      val expectedErrors = List(
        DomainError.EmptyCustomerId("Empty Customer ID"),
        DomainError.EmptyItemSku("Empty item sku"),
        DomainError.InvalidItemQuantity("Item quantity is negative", LineItem.Sku("")),
        DomainError.InvalidItemQuantity("Item quantity is negative", secondInvalidItem.sku),
        DomainError.EmptyCouponCode("Coupon code set but empty")
      )

      expect(errorsOf(invalidOrder) == expectedErrors)
    }
  }

  test(
    "When a customer order contains no line items, collection validation fails without suppressing independent request failures"
  ) {
    forall(DomainGenerators.validCustomerOrder) { order =>
      val invalidOrder = order
        .focus(_.customerId)
        .replace(Customer.CustomerId(""))
        .focus(_.items)
        .replace(Nil)
        .focus(_.couponCode)
        .replace(Some(Coupon.CouponCode("")))

      val expectedErrors = List(
        DomainError.EmptyCustomerId("Empty Customer ID"),
        DomainError.EmptyLineItemList("List of items is empty"),
        DomainError.EmptyCouponCode("Coupon code set but empty")
      )

      expect(errorsOf(invalidOrder) == expectedErrors)
    }
  }

  test("A customer order may omit a coupon, but a supplied coupon code must contain a value") {
    forall(DomainGenerators.validCustomerOrder) { order =>
      val withoutCoupon = order.focus(_.couponCode).replace(None)
      val withCoupon = order
        .focus(_.couponCode)
        .replace(Some(Coupon.CouponCode(order.customerId.value)))

      expect(ValidationService.validate(withoutCoupon).toOption.contains(withoutCoupon)) and
        expect(ValidationService.validate(withCoupon).toOption.contains(withCoupon))
    }
  }

  private def errorsOf(order: CustomerOrder): List[DomainError] =
    ValidationService.validate(order).swap.toOption.fold(List.empty)(_.toList)
}

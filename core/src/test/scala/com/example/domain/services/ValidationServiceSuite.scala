package com.example.domain.services

import cats.syntax.all.*
import com.example.domain.models.DomainError
import com.example.generators.DomainGenerators
import com.example.generators.DomainGenerators.given
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object ValidationServiceSuite extends SimpleIOSuite with Checkers {

  test("valid customer orders are preserved") {
    forall(DomainGenerators.validCustomerOrder) { order =>
      expect(ValidationService.validate(order).toOption.contains(order))
    }
  }

  test("an empty customer id is rejected") {
    forall(DomainGenerators.validCustomerOrder) { order =>
      val result = ValidationService.validate(order.copy(customerId = DomainGenerators.emptyCustomerId))

      expect(result.swap.toOption.exists(_.exists(_.isInstanceOf[DomainError.EmptyCustomerId])))
    }
  }

  test("an empty item list is rejected") {
    forall(DomainGenerators.validCustomerOrder) { order =>
      val result = ValidationService.validate(order.copy(items = DomainGenerators.emptyItems))

      expect(result.swap.toOption.exists(_.exists(_.isInstanceOf[DomainError.EmptyLineItemList])))
    }
  }

  test("an empty item sku is rejected") {
    forall(DomainGenerators.validCustomerOrder) { order =>
      val invalidItem = order.items.head.copy(sku = DomainGenerators.emptySku)
      val result = ValidationService.validate(order.copy(items = invalidItem :: order.items.tail))

      expect(result.swap.toOption.exists(_.exists(_.isInstanceOf[DomainError.EmptyItemSku])))
    }
  }

  test("a non-positive item quantity is rejected") {
    val scenario = for {
      order <- DomainGenerators.validCustomerOrder
      quantity <- DomainGenerators.nonPositiveQuantity
    } yield (order, quantity)

    forall(scenario) { case (order, quantity) =>
      val invalidItem = order.items.head.copy(quantity = quantity)
      val result = ValidationService.validate(order.copy(items = invalidItem :: order.items.tail))

      expect(result.swap.toOption.exists(_.exists(_.isInstanceOf[DomainError.InvalidItemQuantity])))
    }
  }

  test("an empty supplied coupon code is rejected") {
    forall(DomainGenerators.validCustomerOrder) { order =>
      val result = ValidationService.validate(
        order.copy(couponCode = Some(DomainGenerators.emptyCouponCode))
      )

      expect(result.swap.toOption.exists(_.exists(_.isInstanceOf[DomainError.EmptyCouponCode])))
    }
  }

  test("independent validation errors accumulate") {
    val scenario = for {
      order <- DomainGenerators.validCustomerOrder
      quantity <- DomainGenerators.nonPositiveQuantity
    } yield (order, quantity)

    forall(scenario) { case (order, quantity) =>
      val invalidItem = order.items.head.copy(
        sku = DomainGenerators.emptySku,
        quantity = quantity
      )
      val invalidOrder = order.copy(
        customerId = DomainGenerators.emptyCustomerId,
        items = invalidItem :: order.items.tail,
        couponCode = Some(DomainGenerators.emptyCouponCode)
      )
      val errors = ValidationService.validate(invalidOrder).swap.toOption.toList.flatMap(_.toList)

      expect(errors.exists(_.isInstanceOf[DomainError.EmptyCustomerId])) and
        expect(errors.exists(_.isInstanceOf[DomainError.EmptyItemSku])) and
        expect(errors.exists(_.isInstanceOf[DomainError.InvalidItemQuantity])) and
        expect(errors.exists(_.isInstanceOf[DomainError.EmptyCouponCode]))
    }
  }
}

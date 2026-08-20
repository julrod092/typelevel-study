package com.example.domain.services

import cats.syntax.all.*
import com.example.domain.models.{CustomerLineItem, DomainError}
import com.example.generators.DomainGenerators
import com.example.generators.DomainGenerators.{PricingScenario, given}
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object OrderPriceServiceSuite extends SimpleIOSuite with Checkers {

  private def price(scenario: PricingScenario) =
    OrderPriceService.createOrder(
      scenario.customerOrder,
      scenario.customer,
      None,
      scenario.orderId,
      scenario.now
    )

  test("supported items produce internally consistent order totals") {
    forall(DomainGenerators.pricingScenario) { scenario =>
      price(scenario).toEither match {
        case Right(order) =>
          val expectedLineItems = scenario.expectedItems.map { item =>
            (
              item.value.sku,
              item.value.quantity,
              item.unitPrice,
              item.unitPrice * item.value.quantity
            )
          }
          val actualLineItems = order.items.map { item =>
            (item.sku, item.quantity, item.unitPrice, item.lineTotal)
          }
          val expectedSubtotal = expectedLineItems.map(_._4).sum

          expect(actualLineItems == expectedLineItems) and
            expect(order.subtotal == expectedSubtotal) and
            expect(order.discountAmount == DomainGenerators.noDiscount) and
            expect(order.total == expectedSubtotal) and
            expect(order.couponCode.isEmpty)
        case Left(errors) => failure(errors.toString)
      }
    }
  }

  test("generated metadata is preserved exactly") {
    forall(DomainGenerators.pricingScenario) { scenario =>
      price(scenario).toEither match {
        case Right(order) =>
          expect(order.orderId == scenario.orderId) and
            expect(order.createdAt == scenario.now) and
            expect(order.updatedAt == scenario.now)
        case Left(errors) => failure(errors.toString)
      }
    }
  }

  test("a valid coupon determines the discount and total") {
    val scenario = for {
      pricing <- DomainGenerators.applicablePricingScenario
      subtotal = DomainGenerators.subtotal(pricing)
      coupon <- DomainGenerators.validCoupon(pricing.now, subtotal)
    } yield (pricing, coupon)

    forall(scenario) { case (pricing, coupon) =>
      val request = pricing.customerOrder.copy(couponCode = Some(coupon.code))
      val result = OrderPriceService.createOrder(
        request,
        pricing.customer,
        Some(coupon),
        pricing.orderId,
        pricing.now
      )

      result.toEither match {
        case Right(order) =>
          val expectedDiscount = DomainGenerators.discountAmount(
            order.subtotal,
            coupon.discountPercent
          )

          expect(order.discountAmount == expectedDiscount) and
            expect(order.total == order.subtotal - expectedDiscount) and
            expect(order.couponCode.contains(coupon.code))
        case Left(errors) => failure(errors.toString)
      }
    }
  }

  test("an unsupported sku is rejected") {
    val scenario = for {
      pricing <- DomainGenerators.pricingScenario
      generatedSku <- DomainGenerators.unsupportedSku
    } yield (pricing, generatedSku)

    forall(scenario) { case (pricing, generatedSku) =>
      val invalidItem = pricing.customerOrder.items.head.copy(sku = generatedSku)
      val invalidOrder = pricing.customerOrder.copy(
        items = invalidItem :: pricing.customerOrder.items.tail
      )
      val result = OrderPriceService.createOrder(
        invalidOrder,
        pricing.customer,
        None,
        pricing.orderId,
        pricing.now
      )

      expect(result.swap.toOption.exists(_.exists(_.isInstanceOf[DomainError.UnknownSku])))
    }
  }

  test("multiple unsupported skus accumulate errors") {
    val scenario = for {
      pricing <- DomainGenerators.pricingScenario
      firstSku <- DomainGenerators.unsupportedSku
      secondSku <- DomainGenerators.unsupportedSku
      firstQuantity <- DomainGenerators.positiveQuantity
      secondQuantity <- DomainGenerators.positiveQuantity
    } yield (
      pricing,
      List(CustomerLineItem(firstSku, firstQuantity), CustomerLineItem(secondSku, secondQuantity))
    )

    forall(scenario) { case (pricing, invalidItems) =>
      val result = OrderPriceService.createOrder(
        pricing.customerOrder.copy(items = invalidItems),
        pricing.customer,
        None,
        pricing.orderId,
        pricing.now
      )
      val unknownSkuCount = result.swap.toOption.fold(0)(
        _.count(_.isInstanceOf[DomainError.UnknownSku])
      )

      expect(unknownSkuCount == invalidItems.size)
    }
  }

  test("an expired coupon is rejected") {
    invalidCouponProperty(DomainGenerators.expiredCoupon) { error =>
      error.isInstanceOf[DomainError.CouponExpirationError]
    }
  }

  test("an exhausted coupon is rejected") {
    invalidCouponProperty(DomainGenerators.exhaustedCoupon) { error =>
      error.isInstanceOf[DomainError.CouponLimitReached]
    }
  }

  test("a non-stackable coupon is rejected") {
    invalidCouponProperty(DomainGenerators.nonStackableCoupon) { error =>
      error.isInstanceOf[DomainError.CouponNotStackable]
    }
  }

  test("a coupon cannot stack with an inapplicable customer tier") {
    val scenario = for {
      pricing <- DomainGenerators.inapplicablePricingScenario
      subtotal = DomainGenerators.subtotal(pricing)
      coupon <- DomainGenerators.validCoupon(pricing.now, subtotal)
    } yield (pricing, coupon)

    forall(scenario) { case (pricing, coupon) =>
      val result = OrderPriceService.createOrder(
        pricing.customerOrder.copy(couponCode = Some(coupon.code)),
        pricing.customer,
        Some(coupon),
        pricing.orderId,
        pricing.now
      )

      expect(
        result.swap.toOption.exists(_.exists(_.isInstanceOf[DomainError.CouponNotStackable]))
      )
    }
  }

  test("a coupon above the order subtotal is rejected") {
    invalidCouponProperty(DomainGenerators.underMinimumCoupon) { error =>
      error.isInstanceOf[DomainError.CouponUnderExpectedAmount]
    }
  }

  test("an invalid coupon discount percentage is rejected") {
    invalidCouponProperty(DomainGenerators.invalidDiscountCoupon) { error =>
      error.isInstanceOf[DomainError.CouponInvalidDiscountPercentage]
    }
  }

  test("pricing does not alter its inputs") {
    forall(DomainGenerators.pricingScenario) { scenario =>
      val originalOrder = scenario.customerOrder
      val originalCustomer = scenario.customer

      price(scenario)

      expect(scenario.customerOrder == originalOrder) and
        expect(scenario.customer == originalCustomer)
    }
  }

  private def invalidCouponProperty(
      couponGenerator: (java.time.Instant, BigDecimal) => Gen[com.example.domain.models.Coupon]
  )(matches: DomainError => Boolean) = {
    val scenario = for {
      pricing <- DomainGenerators.applicablePricingScenario
      subtotal = DomainGenerators.subtotal(pricing)
      coupon <- couponGenerator(pricing.now, subtotal)
    } yield (pricing, coupon)

    forall(scenario) { case (pricing, coupon) =>
      val result = OrderPriceService.createOrder(
        pricing.customerOrder.copy(couponCode = Some(coupon.code)),
        pricing.customer,
        Some(coupon),
        pricing.orderId,
        pricing.now
      )

      expect(result.swap.toOption.exists(_.exists(matches)))
    }
  }
}

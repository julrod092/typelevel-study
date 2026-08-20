package com.example.domain.services

import cats.data.ValidatedNec
import cats.syntax.all.*
import com.example.domain.models.*
import com.example.generators.DomainGenerators
import com.example.generators.DomainGenerators.given
import monocle.syntax.all.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object OrderPriceServiceSuite extends SimpleIOSuite with Checkers {

  private val catalog = List(
    LineItem.Sku("SKU-001") -> BigDecimal("19.99"),
    LineItem.Sku("SKU-045") -> BigDecimal("49.99"),
    LineItem.Sku("SKU-062") -> BigDecimal("32.99"),
    LineItem.Sku("SKU-430") -> BigDecimal("109.00")
  )

  test(
    "When a customer submits catalog-backed lines without a coupon, pricing creates a complete order with exact catalog amounts and metadata"
  ) {
    val scenario = for {
      generatedRequest <- DomainGenerators.validCustomerOrder
      generatedCustomer <- DomainGenerators.customer
      metadata <- DomainGenerators.order
    } yield (generatedRequest, generatedCustomer, metadata)

    forall(scenario) { case (generatedRequest, generatedCustomer, metadata) =>
      val baseItem = generatedRequest.items.head
      val requestItems = catalog.map { case (sku, _) =>
        baseItem.focus(_.sku).replace(sku)
      }
      val request = generatedRequest
        .focus(_.items)
        .replace(requestItems)
        .focus(_.couponCode)
        .replace(None)
      val customer = generatedCustomer
        .focus(_.customerId)
        .replace(request.customerId)
        .focus(_.tier)
        .replace(CustomerTier.GOLD)
      val now = metadata.createdAt

      OrderPriceService
        .createOrder(request, customer, None, metadata.orderId, now)
        .toEither match {
        case Right(order) =>
          val expectedItems = catalog.map { case (sku, unitPrice) =>
            LineItem(sku, baseItem.quantity, unitPrice, unitPrice * baseItem.quantity)
          }
          val expectedSubtotal = expectedItems.map(_.lineTotal).sum

          expect(order.orderId == metadata.orderId) and
            expect(order.customerId == request.customerId) and
            expect(order.status == OrderStatus.InProgress) and
            expect(order.items == expectedItems) and
            expect(order.subtotal == expectedSubtotal) and
            expect(order.discountAmount == BigDecimal(0)) and
            expect(order.total == expectedSubtotal) and
            expect(order.couponCode.isEmpty) and
            expect(order.createdAt == now) and
            expect(order.updatedAt == now)
        case Left(errors) => failure(errors.toString)
      }
    }
  }

  test(
    "When a qualified customer applies a valid coupon, pricing returns the complete discounted order and preserves order identity"
  ) {
    val scenario = for {
      generatedRequest <- DomainGenerators.validCustomerOrder
      generatedCustomer <- DomainGenerators.customer
      generatedCoupon <- DomainGenerators.coupon
      metadata <- DomainGenerators.order
    } yield (generatedRequest, generatedCustomer, generatedCoupon, metadata)

    forall(scenario) { case (generatedRequest, generatedCustomer, generatedCoupon, metadata) =>
      val now = metadata.createdAt
      val item = generatedRequest.items.head.focus(_.sku).replace(catalog.head._1)
      val request = generatedRequest
        .focus(_.items)
        .replace(List(item))
        .focus(_.couponCode)
        .replace(Some(generatedCoupon.code))
      val customer = generatedCustomer
        .focus(_.customerId)
        .replace(request.customerId)
        .focus(_.tier)
        .replace(CustomerTier.GOLD)
      val coupon = validCoupon(generatedCoupon, now, minOrderAmount = BigDecimal(0))

      OrderPriceService
        .createOrder(request, customer, Some(coupon), metadata.orderId, now)
        .toEither match {
        case Right(order) =>
          val expectedSubtotal = catalog.head._2 * item.quantity
          val expectedDiscount = expectedSubtotal * coupon.discountPercent / 100

          expect(order.orderId == metadata.orderId) and
            expect(order.customerId == request.customerId) and
            expect(
              order.items == List(
                LineItem(item.sku, item.quantity, catalog.head._2, expectedSubtotal)
              )
            ) and
            expect(order.subtotal == expectedSubtotal) and
            expect(order.discountAmount == expectedDiscount) and
            expect(order.total == expectedSubtotal - expectedDiscount) and
            expect(order.couponCode.contains(coupon.code)) and
            expect(order.createdAt == now) and
            expect(order.updatedAt == now)
        case Left(errors) => failure(errors.toString)
      }
    }
  }

  test(
    "When an order contains several unknown catalog entries, pricing reports every unknown SKU in request order and does not evaluate coupon policy"
  ) {
    val scenario = for {
      generatedRequest <- DomainGenerators.validCustomerOrder
      generatedCustomer <- DomainGenerators.customer
      generatedCoupon <- DomainGenerators.coupon
      metadata <- DomainGenerators.order
    } yield (generatedRequest, generatedCustomer, generatedCoupon, metadata)

    forall(scenario) { case (generatedRequest, generatedCustomer, generatedCoupon, metadata) =>
      val now = metadata.createdAt
      val firstItem = generatedRequest.items.head
      val secondItem = firstItem
        .focus(_.sku)
        .modify(sku => LineItem.Sku(s"${sku.value}X"))
      val request = generatedRequest
        .focus(_.items)
        .replace(List(firstItem, secondItem))
        .focus(_.couponCode)
        .replace(Some(generatedCoupon.code))
      val customer = generatedCustomer
        .focus(_.customerId)
        .replace(request.customerId)
        .focus(_.tier)
        .replace(CustomerTier.BASIC)
      val invalidCoupon = generatedCoupon
        .focus(_.discountPercent)
        .replace(0)
        .focus(_.minOrderAmount)
        .replace(BigDecimal("999999"))
        .focus(_.usageLimit)
        .replace(1)
        .focus(_.usageCount)
        .replace(1)
        .focus(_.expiresAt)
        .replace(now)
        .focus(_.stackableWithTiers)
        .replace(false)

      val result = OrderPriceService.createOrder(
        request,
        customer,
        Some(invalidCoupon),
        metadata.orderId,
        now
      )
      val expectedErrors = List(
        DomainError.UnknownSku("Item sku not found", firstItem.sku),
        DomainError.UnknownSku("Item sku not found", secondItem.sku)
      )

      expect(errorsOf(result) == expectedErrors)
    }
  }

  test(
    "When a coupon violates every pricing policy, pricing returns every coupon failure in policy evaluation order"
  ) {
    val scenario = for {
      generatedRequest <- DomainGenerators.validCustomerOrder
      generatedCustomer <- DomainGenerators.customer
      generatedCoupon <- DomainGenerators.coupon
      metadata <- DomainGenerators.order
    } yield (generatedRequest, generatedCustomer, generatedCoupon, metadata)

    forall(scenario) { case (generatedRequest, generatedCustomer, generatedCoupon, metadata) =>
      val now = metadata.createdAt
      val item = generatedRequest.items.head.focus(_.sku).replace(catalog.head._1)
      val subtotal = catalog.head._2 * item.quantity
      val request = generatedRequest
        .focus(_.items)
        .replace(List(item))
        .focus(_.couponCode)
        .replace(Some(generatedCoupon.code))
      val customer = generatedCustomer
        .focus(_.customerId)
        .replace(request.customerId)
        .focus(_.tier)
        .replace(CustomerTier.BASIC)
      val invalidCoupon = generatedCoupon
        .focus(_.discountPercent)
        .replace(0)
        .focus(_.minOrderAmount)
        .replace(subtotal + BigDecimal("0.01"))
        .focus(_.usageLimit)
        .replace(1)
        .focus(_.usageCount)
        .replace(1)
        .focus(_.expiresAt)
        .replace(now)
        .focus(_.stackableWithTiers)
        .replace(false)

      val result = OrderPriceService.createOrder(
        request,
        customer,
        Some(invalidCoupon),
        metadata.orderId,
        now
      )
      val expectedErrors = List(
        DomainError.CouponExpirationError("Coupon Expired", invalidCoupon.code),
        DomainError.CouponLimitReached("Coupon uses passed its limit", invalidCoupon.code),
        DomainError.CouponNotStackable("Coupon not apply for customer tier", invalidCoupon.code),
        DomainError.CouponUnderExpectedAmount(
          "Order does not meet expected amount for discount",
          invalidCoupon.code
        ),
        DomainError.CouponInvalidDiscountPercentage(
          "Coupon discount percentage is an invalid value",
          invalidCoupon.code
        )
      )

      expect(errorsOf(result) == expectedErrors)
    }
  }

  test(
    "Coupon policy boundaries distinguish accepted values from the first rejected expiration, usage, amount, percentage, and tier value"
  ) {
    val scenario = for {
      generatedRequest <- DomainGenerators.validCustomerOrder
      generatedCustomer <- DomainGenerators.customer
      generatedCoupon <- DomainGenerators.coupon
      metadata <- DomainGenerators.order
    } yield (generatedRequest, generatedCustomer, generatedCoupon, metadata)

    forall(scenario) { case (generatedRequest, generatedCustomer, generatedCoupon, metadata) =>
      val now = metadata.createdAt
      val item = generatedRequest.items.head.focus(_.sku).replace(catalog.head._1)
      val subtotal = catalog.head._2 * item.quantity
      val request = generatedRequest
        .focus(_.items)
        .replace(List(item))
        .focus(_.couponCode)
        .replace(Some(generatedCoupon.code))
      val customer = generatedCustomer
        .focus(_.customerId)
        .replace(request.customerId)
        .focus(_.tier)
        .replace(CustomerTier.SILVER)
      val coupon = validCoupon(generatedCoupon, now, minOrderAmount = subtotal)

      def price(candidateCoupon: Coupon, candidateCustomer: Customer = customer) =
        OrderPriceService.createOrder(
          request,
          candidateCustomer,
          Some(candidateCoupon),
          metadata.orderId,
          now
        )

      val acceptedAtOnePercent = price(
        coupon.focus(_.discountPercent).replace(1),
        customer
      )
      val acceptedAtNinetyNinePercent = price(
        coupon.focus(_.discountPercent).replace(99),
        customer
      )
      val acceptedForGold = price(
        coupon,
        customer.focus(_.tier).replace(CustomerTier.GOLD)
      )
      val expiresNow = coupon.focus(_.expiresAt).replace(now)
      val usageAtLimit = coupon.focus(_.usageCount).replace(coupon.usageLimit)
      val minimumAboveSubtotal = coupon
        .focus(_.minOrderAmount)
        .replace(subtotal + BigDecimal("0.01"))
      val zeroPercent = coupon.focus(_.discountPercent).replace(0)
      val oneHundredPercent = coupon.focus(_.discountPercent).replace(100)
      val basicCustomer = customer.focus(_.tier).replace(CustomerTier.BASIC)
      val nonStackable = coupon.focus(_.stackableWithTiers).replace(false)

      expect(acceptedAtOnePercent.isValid) and
        expect(acceptedAtNinetyNinePercent.isValid) and
        expect(acceptedForGold.isValid) and
        expect(
          errorsOf(price(expiresNow, customer)) == List(
            DomainError.CouponExpirationError("Coupon Expired", coupon.code)
          )
        ) and
        expect(
          errorsOf(price(usageAtLimit, customer)) == List(
            DomainError.CouponLimitReached("Coupon uses passed its limit", coupon.code)
          )
        ) and
        expect(
          errorsOf(price(minimumAboveSubtotal, customer)) == List(
            DomainError.CouponUnderExpectedAmount(
              "Order does not meet expected amount for discount",
              coupon.code
            )
          )
        ) and
        expect(
          errorsOf(price(zeroPercent, customer)) == List(
            DomainError.CouponInvalidDiscountPercentage(
              "Coupon discount percentage is an invalid value",
              coupon.code
            )
          )
        ) and
        expect(
          errorsOf(price(oneHundredPercent, customer)) == List(
            DomainError.CouponInvalidDiscountPercentage(
              "Coupon discount percentage is an invalid value",
              coupon.code
            )
          )
        ) and
        expect(
          errorsOf(price(coupon, basicCustomer)) == List(
            DomainError.CouponNotStackable(
              "Coupon not apply for customer tier",
              coupon.code
            )
          )
        ) and
        expect(
          errorsOf(price(nonStackable, customer)) == List(
            DomainError.CouponNotStackable(
              "Coupon not apply for customer tier",
              coupon.code
            )
          )
        )
    }
  }

  private def validCoupon(
      coupon: Coupon,
      now: java.time.Instant,
      minOrderAmount: BigDecimal
  ): Coupon =
    coupon
      .focus(_.discountPercent)
      .replace(10)
      .focus(_.minOrderAmount)
      .replace(minOrderAmount)
      .focus(_.usageLimit)
      .replace(2)
      .focus(_.usageCount)
      .replace(1)
      .focus(_.expiresAt)
      .replace(now.plusSeconds(1))
      .focus(_.stackableWithTiers)
      .replace(true)

  private def errorsOf(result: ValidatedNec[DomainError, Order]): List[DomainError] =
    result.swap.toOption.fold(List.empty)(_.toList)
}

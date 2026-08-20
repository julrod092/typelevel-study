package com.example.infrastructure.errors

import cats.data.NonEmptyChain
import cats.effect.IO
import com.example.domain.models.DomainError.*
import com.example.domain.models.{Coupon, LineItem}
import pricing.*
import weaver.SimpleIOSuite

object ErrorHandlerSuite extends SimpleIOSuite {

  test("Request-level domain failures preserve their code, message, category, and source order") {
    val errors = NonEmptyChain.of(
      EmptyCustomerId("customer is required"),
      EmptyLineItemList("items are required"),
      EmptyItemSku("sku is required"),
      EmptyCouponCode("coupon code is required")
    )
    val expected = List(
      ValidationError.generalError(
        GeneralValidationError("EMPTY_CUSTOMER_ID", "customer is required")
      ),
      ValidationError.generalError(
        GeneralValidationError("EMPTY_ITEM_LIST", "items are required")
      ),
      ValidationError.generalError(
        GeneralValidationError("EMPTY_ITEM_SKU", "sku is required")
      ),
      ValidationError.generalError(
        GeneralValidationError("EMPTY_COUPON_CODE", "coupon code is required")
      )
    )

    IO.pure(expect(mapped(errors) == expected))
  }

  test("Line-item domain failures retain the affected SKU in the public validation contract") {
    val unknownSku = LineItem.Sku("UNKNOWN-1")
    val invalidQuantitySku = LineItem.Sku("SKU-001")
    val errors = NonEmptyChain.of(
      UnknownSku("sku is not in the catalog", unknownSku),
      InvalidItemQuantity("quantity must be positive", invalidQuantitySku)
    )
    val expected = List(
      ValidationError.itemError(
        ItemValidationError("UNKNOW_SKU", "sku is not in the catalog", Some(unknownSku.value))
      ),
      ValidationError.itemError(
        ItemValidationError(
          "INVALID_ITEM_QUANITY",
          "quantity must be positive",
          Some(invalidQuantitySku.value)
        )
      )
    )

    IO.pure(expect(mapped(errors) == expected))
  }

  test(
    "Every coupon policy failure, including an invalid percentage, maps to the coupon validation contract"
  ) {
    val code = Coupon.CouponCode("SAVE10")
    val errors = NonEmptyChain.of(
      CouponLimitReached("usage limit reached", code),
      CouponNotStackable("tier cannot stack", code),
      CouponExpirationError("coupon expired", code),
      CouponUnderExpectedAmount("minimum not reached", code),
      CouponInvalidDiscountPercentage("percentage is invalid", code)
    )
    val expected = List(
      ValidationError.couponError(
        CouponValidationError("COUPON_LIMIT_REACHED", "usage limit reached", Some(code.value))
      ),
      ValidationError.couponError(
        CouponValidationError("COUPON_NOT_STACKABLE", "tier cannot stack", Some(code.value))
      ),
      ValidationError.couponError(
        CouponValidationError("COUPON_EXPIRED", "coupon expired", Some(code.value))
      ),
      ValidationError.couponError(
        CouponValidationError(
          "COUPON_UNDER_EXPECTED_AMOUNT",
          "minimum not reached",
          Some(code.value)
        )
      ),
      ValidationError.couponError(
        CouponValidationError(
          "INVALID_DISCOUNT_PERCENTAGE",
          "percentage is invalid",
          Some(code.value)
        )
      )
    )

    IO.pure(expect(mapped(errors) == expected))
  }

  private def mapped(errors: NonEmptyChain[com.example.domain.models.DomainError]) =
    ErrorHandler
      .handleDomainErrors(errors)
      .project
      .validationErrors
      .fold(List.empty[ValidationError])(_.errors)
}

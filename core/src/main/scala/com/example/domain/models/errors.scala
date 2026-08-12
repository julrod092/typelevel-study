package com.example.domain.models

enum DomainError(val errorCode: String) {
  case EmptyCustomerId(message: String) extends DomainError("EMPTY_CUSTOMER_ID")
  case EmptyItemSku(message: String) extends DomainError("EMPTY_ITEM_SKU")
  case EmptyLineItemList(message: String) extends DomainError("EMPTY_ITEM_LIST")
  case EmptyCouponCode(message: String) extends DomainError("EMPTY_COUPON_CODE")
  case UnknownSku(message: String, sku: LineItem.Sku) extends DomainError("UNKNOW_SKU")
  case InvalidItemQuantity(message: String, sku: LineItem.Sku)
      extends DomainError("INVALID_ITEM_QUANITY")
  case CouponLimitReached(message: String, code: Coupon.CouponCode)
      extends DomainError("COUPON_LIMIT_REACHED")
  case CouponNotStackable(message: String, code: Coupon.CouponCode)
      extends DomainError("COUPON_NOT_STACKABLE")
  case CouponExpirationError(message: String, code: Coupon.CouponCode)
      extends DomainError("COUPON_EXPIRED")
  case CouponUnderExpectedAmount(message: String, code: Coupon.CouponCode)
      extends DomainError("COUPON_UNDER_EXPECTED_AMOUNT")
  case CouponInvalidDiscountPercentage(message: String, code: Coupon.CouponCode)
      extends DomainError("INVALID_DISCOUNT_PERCENTAGE")
}

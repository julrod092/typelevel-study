package com.example.domain.models

import com.example.domain.models.Customer.CustomerId

enum PricingError(val errorCode: String) {
  case EmptyCustomerId(message: String) extends PricingError("EMPTY_CUSTOMER_ID")
  case EmptyItemSku(message: String) extends PricingError("EMPTY_ITEM_SKU")
  case EmptyLineItemList(message: String) extends PricingError("EMPTY_ITEM_LIST")
  case EmptyCouponCode(message: String) extends PricingError("EMPTY_COUPON_CODE")
  case UnknownSku(message: String, sku: LineItem.Sku) extends PricingError("UNKNOW_SKU")
  case InvalidItemQuantity(message: String, sku: LineItem.Sku)
      extends PricingError("INVALID_ITEM_QUANITY")
  case CouponLimitReached(message: String, code: Coupon.CouponCode)
      extends PricingError("COUPON_LIMIT_REACHED")
  case CouponNotStackable(message: String, code: Coupon.CouponCode)
      extends PricingError("COUPON_NOT_STACKABLE")
  case CouponExpirationError(message: String, code: Coupon.CouponCode)
      extends PricingError("COUPON_EXPIRED")
  case CouponUnderExpectedAmount(message: String, code: Coupon.CouponCode)
      extends PricingError("COUPON_UNDER_EXPECTED_AMOUNT")
  case CouponInvalidDiscountPercentage(message: String, code: Coupon.CouponCode)
      extends PricingError("INVALID_DISCOUNT_PERCENTAGE")
  case CustomerNotFound(message: String, customerId: CustomerId)
      extends PricingError("CUSTOMER_NOT_FOUND")
  case CouponNotFound(message: String, code: Coupon.CouponCode)
      extends PricingError("COUPON_NOT FOUND")
  case OrderNotSaved(message: String, id: Order.OrderId) extends PricingError("ORDER_NOT_SAVED")
  case OrderNotFound(message: String, id: Order.OrderId) extends PricingError("ORDER_NOT_FOUND")
}

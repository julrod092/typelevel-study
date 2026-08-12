package com.example.domain.models

enum PrincingError(errorId: String) {
  case EmptyCustomerId(message: String) extends PrincingError("EMPTY_CUSTOMER_ID")
  case EmptyItemSku(message: String) extends PrincingError("EMPTY_ITEM_SKU")
  case EmptyLineItemList(message: String) extends PrincingError("EMPTY_ITEM_LIST")
  case EmptyCouponCode(message: String) extends PrincingError("EMPTY_COUPON_CODE")
  case UnkownSku(message: String, sku: LineItem.Sku) extends PrincingError("UNKNOW_SKU")
  case InvalidItemQuantity(message: String, sku: LineItem.Sku)
      extends PrincingError("INVALID_ITEM_QUANITY")
  case CouponLimitReached(message: String, code: Coupon.CouponCode)
      extends PrincingError("COUPON_LIMIT_REACHED")
  case CouponNotStackable(message: String, code: Coupon.CouponCode)
      extends PrincingError("COUPON_NOT_STACKABLE")
  case CouponExpirationError(message: String, code: Coupon.CouponCode)
      extends PrincingError("COUPON_EXPIRED")
  case CouponUnderExpectedAmount(message: String, code: Coupon.CouponCode)
      extends PrincingError("COUPON_UNDER_EXPECTED_AMOUNT")
  case CoupontInvalidDiscountPercentage(message: String, code: Coupon.CouponCode)
      extends PrincingError("INVALID_DISCOUNT_PERCENTAGE")
  case InvalidOrderAmountAggregation(message: String, orderId: Order.OrderId)
      extends PrincingError("INVALID_ORDER_AMOUNT_AGGREGATION")
}

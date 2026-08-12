package com.example.domain.models

enum PrincingError(labelId: String) {
  case UnkownSku(message: String, sku: LineItem.Sku) extends PrincingError("UNKNOW_SKU")
  case InvalidItemQuantity(message: String, sku: LineItem.Sku)
      extends PrincingError("INVALID_ITEM_QUANITY")
  case CouponLimitReached(message: String, code: Coupon.CouponCode)
      extends PrincingError("COUPON_LIMIT_REACHED")
  case CouponNotStackable(message: String, code: Coupon.CouponCode)
      extends PrincingError("COUPON_NOT_STACKABLE")
  case InvalidOrderAmountAggregation(message: String, orderId: Order.OrderId)
      extends PrincingError("INVALID_ORDER_AMOUNT_AGGREGATION")
}

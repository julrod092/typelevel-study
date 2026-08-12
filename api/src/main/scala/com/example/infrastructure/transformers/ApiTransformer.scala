package com.example.infrastructure.transformers

import com.example.domain.models.{
  Customer,
  LineItem,
  Order,
  OrderStatus,
  CustomerOrder,
  CustomerLineItem
}
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.Transformer
import pricing.{LineItemDTO, OrderPriceDTO, OrderPricedDTO}
import com.example.domain.models.Coupon
import com.example.domain.repositories.OrderRecord
import com.example.domain.repositories.LineItemRecord
import java.time.Instant

object ApiTransformer {

  given Transformer[LineItemDTO, CustomerLineItem] =
    Transformer
      .define[LineItemDTO, CustomerLineItem]
      .withFieldComputed(_.sku, value => LineItem.Sku(value.sku))
      .buildTransformer

  given Transformer[OrderPriceDTO, CustomerOrder] =
    Transformer
      .define[OrderPriceDTO, CustomerOrder]
      .withFieldComputed(_.customerId, value => Customer.CustomerId(value.customerId))
      .withFieldComputed(_.couponCode, value => value.couponCode.map(Coupon.CouponCode(_)))
      .buildTransformer

  given Transformer[LineItem, LineItemDTO] =
    Transformer
      .define[LineItem, LineItemDTO]
      .withFieldComputed(_.sku, _.sku.value)
      .buildTransformer

  given Transformer[Order, OrderPricedDTO] =
    Transformer
      .define[Order, OrderPricedDTO]
      .withFieldComputed(_.orderId, _.orderId.value)
      .withFieldComputed(_.customerId, _.customerId.value)
      .withFieldComputed(_.status, _.status.toString)
      .withFieldComputed(_.createdAt, _.createdAt.toString())
      .withFieldComputed(_.couponApplied, _.couponCode.value)
      .buildTransformer

}

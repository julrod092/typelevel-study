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

  given Transformer[LineItem, LineItemRecord] =
    Transformer
      .define[LineItem, LineItemRecord]
      .withFieldComputed(_.sku, _.sku.value)
      .buildTransformer

  given Transformer[LineItemRecord, LineItem] =
    Transformer
      .define[LineItemRecord, LineItem]
      .withFieldComputed(_.sku, value => LineItem.Sku(value.sku))
      .buildTransformer

  given Transformer[LineItem, LineItemDTO] =
    Transformer
      .define[LineItem, LineItemDTO]
      .withFieldComputed(_.sku, _.sku.value)
      .buildTransformer

  given Transformer[Order, OrderRecord] =
    Transformer
      .define[Order, OrderRecord]
      .withFieldComputed(_.orderId, _.orderId.value)
      .withFieldComputed(_.customerId, _.customerId.value)
      .withFieldComputed(_.status, _.status.toString)
      .withFieldComputed(_.couponCode, _.couponCode.value)
      .withFieldComputed(_.createdAt, _.createdAt.toString())
      .withFieldComputed(_.updatedAt, _.updatedAt.toString())
      .buildTransformer

  given Transformer[OrderRecord, Order] =
    Transformer
      .define[OrderRecord, Order]
      .withFieldComputed(_.orderId, value => Order.OrderId(value.orderId))
      .withFieldComputed(_.customerId, value => Customer.CustomerId(value.customerId))
      .withFieldComputed(_.status, value => OrderStatus.valueOf(value.status))
      .withFieldComputed(_.couponCode, value => Coupon.CouponCode(value.couponCode))
      .withFieldComputed(_.createdAt, value => Instant.parse(value.createdAt))
      .withFieldComputed(_.updatedAt, value => Instant.parse(value.updatedAt))
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

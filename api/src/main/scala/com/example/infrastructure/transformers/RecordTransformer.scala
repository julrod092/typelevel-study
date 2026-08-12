package com.example.infrastructure.transformers

import com.example.domain.models.{Customer, LineItem, Order, OrderStatus}
import com.example.domain.repositories.{LineItemRecord, OrderRecord}
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

import java.time.Instant
import com.example.domain.models.Coupon

object RecordTransformer {

  given Transformer[LineItemRecord, LineItem] =
    Transformer
      .define[LineItemRecord, LineItem]
      .withFieldComputed(_.sku, value => LineItem.Sku(value.sku))
      .buildTransformer

  given Transformer[LineItem, LineItemRecord] =
    Transformer
      .define[LineItem, LineItemRecord]
      .withFieldComputed(_.sku, _.sku.value)
      .buildTransformer

  given Transformer[OrderRecord, Order] =
    Transformer
      .define[OrderRecord, Order]
      .withFieldComputed(_.orderId, value => Order.OrderId(value.orderId))
      .withFieldComputed(_.customerId, value => Customer.CustomerId(value.customerId))
      .withFieldComputed(_.status, value => OrderStatus.valueOf(value.status))
      .withFieldComputed(_.items, value => value.items.map(_.transformInto[LineItem]))
      .withFieldComputed(_.couponCode, value => Coupon.CouponCode(value.couponCode))
      .withFieldComputed(_.createdAt, value => Instant.parse(value.createdAt))
      .withFieldComputed(_.updatedAt, value => Instant.parse(value.updatedAt))
      .buildTransformer

  given Transformer[Order, OrderRecord] =
    Transformer
      .define[Order, OrderRecord]
      .withFieldComputed(_.orderId, _.orderId.value)
      .withFieldComputed(_.customerId, _.customerId.value)
      .withFieldComputed(_.status, value => value.status.toString)
      .withFieldComputed(_.items, value => value.items.map(_.transformInto[LineItemRecord]))
      .withFieldComputed(_.couponCode, _.couponCode.value)
      .withFieldComputed(_.createdAt, _.toString)
      .withFieldComputed(_.updatedAt, _.toString)
      .buildTransformer

}

package com.example.infrastructure.transformers

import com.example.domain.models.{Customer, LineItem, Order, OrderStatus}
import com.example.domain.repositories.{LineItemRecord, OrderRecord}
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

import java.time.Instant
import com.example.domain.models.Coupon
import com.example.domain.repositories.CustomerRecord
import com.example.domain.repositories.CouponRecord
import com.example.domain.models.CustomerTier

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
      .withFieldComputed(_.couponCode, _.couponCode.map(value => Coupon.CouponCode(value)))
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
      .withFieldComputed(_.couponCode, _.couponCode.map(_.value))
      .withFieldComputed(_.createdAt, _.createdAt.toString)
      .withFieldComputed(_.updatedAt, _.updatedAt.toString)
      .buildTransformer

  given Transformer[CustomerRecord, Customer] =
    Transformer
      .define[CustomerRecord, Customer]
      .withFieldComputed(_.customerId, value => Customer.CustomerId(value.customerId))
      .withFieldComputed(_.tier, value => CustomerTier.valueOf(value.tier))
      .withFieldComputed(_.createdAt, value => Instant.parse(value.createdAt))
      .buildTransformer

  given Transformer[Customer, CustomerRecord] =
    Transformer
      .define[Customer, CustomerRecord]
      .withFieldComputed(_.customerId, _.customerId.value)
      .withFieldComputed(_.tier, _.tier.toString)
      .withFieldComputed(_.createdAt, _.createdAt.toString)
      .buildTransformer

  given Transformer[CouponRecord, Coupon] =
    Transformer
      .define[CouponRecord, Coupon]
      .withFieldComputed(_.code, value => Coupon.CouponCode(value.code))
      .withFieldComputed(_.expiresAt, value => Instant.parse(value.expiresAt))
      .buildTransformer

  given Transformer[Coupon, CouponRecord] =
    Transformer
      .define[Coupon, CouponRecord]
      .withFieldComputed(_.code, _.code.value)
      .withFieldComputed(_.expiresAt, _.expiresAt.toString)
      .buildTransformer

}

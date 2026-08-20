package com.example.generators

import cats.Show
import com.example.domain.models.*
import org.scalacheck.Gen

import java.time.Instant

object DomainGenerators {

  given Show[CustomerOrder] = Show.fromToString
  given Show[CustomerLineItem] = Show.fromToString
  given Show[Customer] = Show.fromToString
  given Show[Coupon] = Show.fromToString
  given Show[LineItem] = Show.fromToString
  given Show[Order] = Show.fromToString
  given Show[LineItem.Sku] = Show.show(_.value)

  val nonEmptyString: Gen[String] =
    Gen.choose(1, 32).flatMap(size => Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString))

  val instant: Gen[Instant] =
    Gen.choose(0L, 4102444800L).map(Instant.ofEpochSecond)

  val positiveAmount: Gen[BigDecimal] =
    Gen.choose(1L, 100000L).map(cents => BigDecimal(cents) / 100)

  val customerLineItem: Gen[CustomerLineItem] = for {
    generatedSku <- nonEmptyString.map(LineItem.Sku(_))
    quantity <- Gen.choose(1, 10)
  } yield CustomerLineItem(generatedSku, quantity)

  val validCustomerOrder: Gen[CustomerOrder] = for {
    generatedCustomerId <- nonEmptyString.map(Customer.CustomerId(_))
    itemCount <- Gen.choose(1, 8)
    items <- Gen.listOfN(itemCount, customerLineItem)
    generatedCouponCode <- Gen.option(nonEmptyString.map(Coupon.CouponCode(_)))
  } yield CustomerOrder(generatedCustomerId, items, generatedCouponCode)

  val customer: Gen[Customer] = for {
    generatedCustomerId <- nonEmptyString.map(Customer.CustomerId(_))
    tier <- Gen.oneOf(CustomerTier.values.toSeq)
    name <- Gen.option(nonEmptyString)
    createdAt <- instant
  } yield Customer(generatedCustomerId, tier, name, createdAt)

  val coupon: Gen[Coupon] = for {
    generatedCouponCode <- nonEmptyString.map(Coupon.CouponCode(_))
    discountPercent <- Gen.choose(-100, 200)
    minOrderAmount <- positiveAmount
    usageLimit <- Gen.choose(0, 1000)
    usageCount <- Gen.choose(0, 1200)
    expiresAt <- instant
    stackableWithTiers <- Gen.oneOf(true, false)
  } yield Coupon(
    generatedCouponCode,
    discountPercent,
    minOrderAmount,
    usageLimit,
    usageCount,
    expiresAt,
    stackableWithTiers
  )

  val lineItem: Gen[LineItem] = for {
    generatedSku <- nonEmptyString.map(LineItem.Sku(_))
    quantity <- Gen.choose(1, 10)
    unitPrice <- positiveAmount
  } yield LineItem(generatedSku, quantity, unitPrice, unitPrice * quantity)

  val order: Gen[Order] = for {
    generatedOrderId <- nonEmptyString.map(Order.OrderId(_))
    generatedCustomerId <- nonEmptyString.map(Customer.CustomerId(_))
    status <- Gen.oneOf(OrderStatus.values.toSeq)
    itemCount <- Gen.choose(0, 8)
    items <- Gen.listOfN(itemCount, lineItem)
    subtotal = items.map(_.lineTotal).sum
    discountRate <- Gen.choose(0, 100)
    discountAmount = subtotal * discountRate / 100
    generatedCouponCode <- Gen.option(nonEmptyString.map(Coupon.CouponCode(_)))
    createdAt <- instant
    updateOffset <- Gen.choose(0L, 86400L)
  } yield Order(
    generatedOrderId,
    generatedCustomerId,
    status,
    items,
    subtotal,
    discountAmount,
    subtotal - discountAmount,
    generatedCouponCode,
    createdAt,
    createdAt.plusSeconds(updateOffset)
  )

  def validCoupon(now: Instant, subtotal: BigDecimal): Gen[Coupon] = for {
    generatedCouponCode <- nonEmptyString.map(Coupon.CouponCode(_))
    discountPercent <- Gen.choose(1, 99)
    minimumCents <- Gen.choose(0L, (subtotal * 100).toLong)
    usageLimit <- Gen.choose(1, 1000)
    usageCount <- Gen.choose(0, usageLimit - 1)
    expirationOffset <- Gen.choose(1L, 31536000L)
  } yield Coupon(
    code = generatedCouponCode,
    discountPercent = discountPercent,
    minOrderAmount = BigDecimal(minimumCents) / 100,
    usageLimit = usageLimit,
    usageCount = usageCount,
    expiresAt = now.plusSeconds(expirationOffset),
    stackableWithTiers = true
  )
}

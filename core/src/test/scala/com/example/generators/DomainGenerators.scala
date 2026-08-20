package com.example.generators

import cats.Show
import com.example.domain.models.*
import org.scalacheck.Gen

import java.time.Instant

object DomainGenerators {

  final case class PricedItemInput(value: CustomerLineItem, unitPrice: BigDecimal)

  final case class PricingScenario(
      customerOrder: CustomerOrder,
      customer: Customer,
      orderId: Order.OrderId,
      now: Instant,
      expectedItems: List[PricedItemInput]
  )

  given Show[CustomerOrder] = Show.fromToString
  given Show[CustomerLineItem] = Show.fromToString
  given Show[Customer] = Show.fromToString
  given Show[Coupon] = Show.fromToString
  given Show[LineItem] = Show.fromToString
  given Show[Order] = Show.fromToString
  given Show[PricingScenario] = Show.fromToString
  given Show[LineItem.Sku] = Show.show(_.value)

  private val supportedProducts: Vector[(LineItem.Sku, BigDecimal)] = Vector(
    LineItem.Sku("SKU-001") -> BigDecimal("19.99"),
    LineItem.Sku("SKU-045") -> BigDecimal("49.99"),
    LineItem.Sku("SKU-062") -> BigDecimal("32.99"),
    LineItem.Sku("SKU-430") -> BigDecimal("109.00")
  )

  val emptyCustomerId: Customer.CustomerId = Customer.CustomerId("")
  val emptySku: LineItem.Sku = LineItem.Sku("")
  val emptyCouponCode: Coupon.CouponCode = Coupon.CouponCode("")
  val emptyItems: List[CustomerLineItem] = Nil
  val noDiscount: BigDecimal = BigDecimal(0)

  val nonEmptyString: Gen[String] =
    Gen.choose(1, 32).flatMap(size => Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString))

  val instant: Gen[Instant] =
    Gen.choose(0L, 4102444800L).map(Instant.ofEpochSecond)

  val customerId: Gen[Customer.CustomerId] = nonEmptyString.map(Customer.CustomerId.apply)

  val couponCode: Gen[Coupon.CouponCode] = nonEmptyString.map(Coupon.CouponCode.apply)

  val orderId: Gen[Order.OrderId] = nonEmptyString.map(Order.OrderId.apply)

  val sku: Gen[LineItem.Sku] = nonEmptyString.map(LineItem.Sku.apply)

  val unsupportedSku: Gen[LineItem.Sku] =
    nonEmptyString.map(value => LineItem.Sku(s"UNSUPPORTED-$value"))

  val positiveQuantity: Gen[Int] = Gen.choose(1, 20)

  val nonPositiveQuantity: Gen[Int] = Gen.choose(-20, 0)

  val positiveAmount: Gen[BigDecimal] =
    Gen.choose(1L, 100000L).map(cents => BigDecimal(cents) / 100)

  val customerTier: Gen[CustomerTier] = Gen.oneOf(CustomerTier.values.toSeq)

  val applicableCustomerTier: Gen[CustomerTier] = Gen.oneOf(CustomerTier.SILVER, CustomerTier.GOLD)

  val customerLineItem: Gen[CustomerLineItem] = for {
    generatedSku <- sku
    quantity <- positiveQuantity
  } yield CustomerLineItem(generatedSku, quantity)

  val validCustomerOrder: Gen[CustomerOrder] = for {
    generatedCustomerId <- customerId
    itemCount <- Gen.choose(1, 8)
    items <- Gen.listOfN(itemCount, customerLineItem)
    generatedCouponCode <- Gen.option(couponCode)
  } yield CustomerOrder(generatedCustomerId, items, generatedCouponCode)

  val customer: Gen[Customer] = for {
    generatedCustomerId <- customerId
    tier <- customerTier
    name <- Gen.option(nonEmptyString)
    createdAt <- instant
  } yield Customer(generatedCustomerId, tier, name, createdAt)

  val coupon: Gen[Coupon] = for {
    generatedCouponCode <- couponCode
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
    generatedSku <- sku
    quantity <- positiveQuantity
    unitPrice <- positiveAmount
  } yield LineItem(generatedSku, quantity, unitPrice, unitPrice * quantity)

  val order: Gen[Order] = for {
    generatedOrderId <- orderId
    generatedCustomerId <- customerId
    status <- Gen.oneOf(OrderStatus.values.toSeq)
    itemCount <- Gen.choose(0, 8)
    items <- Gen.listOfN(itemCount, lineItem)
    subtotal = items.map(_.lineTotal).sum
    discountRate <- Gen.choose(0, 100)
    discountAmount = subtotal * discountRate / 100
    generatedCouponCode <- Gen.option(couponCode)
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

  private val pricedItemInput: Gen[PricedItemInput] = for {
    product <- Gen.oneOf(supportedProducts)
    quantity <- positiveQuantity
    (generatedSku, price) = product
  } yield PricedItemInput(CustomerLineItem(generatedSku, quantity), price)

  val pricingScenario: Gen[PricingScenario] = for {
    generatedCustomerId <- customerId
    itemCount <- Gen.choose(1, 8)
    expectedItems <- Gen.listOfN(itemCount, pricedItemInput)
    tier <- customerTier
    name <- Gen.option(nonEmptyString)
    customerCreatedAt <- instant
    generatedOrderId <- orderId
    now <- instant
  } yield PricingScenario(
    customerOrder = CustomerOrder(generatedCustomerId, expectedItems.map(_.value), None),
    customer = Customer(generatedCustomerId, tier, name, customerCreatedAt),
    orderId = generatedOrderId,
    now = now,
    expectedItems = expectedItems
  )

  val applicablePricingScenario: Gen[PricingScenario] = for {
    scenario <- pricingScenario
    tier <- applicableCustomerTier
  } yield scenario.copy(customer = scenario.customer.copy(tier = tier))

  val inapplicablePricingScenario: Gen[PricingScenario] =
    pricingScenario.map(scenario => scenario.copy(customer = scenario.customer.copy(tier = CustomerTier.BASIC)))

  def validCoupon(now: Instant, subtotal: BigDecimal): Gen[Coupon] = for {
    generatedCouponCode <- couponCode
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

  def expiredCoupon(now: Instant, subtotal: BigDecimal): Gen[Coupon] =
    validCoupon(now, subtotal).flatMap { coupon =>
      Gen.choose(0L, 31536000L).map(offset => coupon.copy(expiresAt = now.minusSeconds(offset)))
    }

  def exhaustedCoupon(now: Instant, subtotal: BigDecimal): Gen[Coupon] =
    validCoupon(now, subtotal).map(coupon => coupon.copy(usageCount = coupon.usageLimit))

  def nonStackableCoupon(now: Instant, subtotal: BigDecimal): Gen[Coupon] =
    validCoupon(now, subtotal).map(_.copy(stackableWithTiers = false))

  def underMinimumCoupon(now: Instant, subtotal: BigDecimal): Gen[Coupon] = for {
    coupon <- validCoupon(now, subtotal)
    difference <- positiveAmount
  } yield coupon.copy(minOrderAmount = subtotal + difference)

  def invalidDiscountCoupon(now: Instant, subtotal: BigDecimal): Gen[Coupon] = for {
    coupon <- validCoupon(now, subtotal)
    invalidDiscount <- Gen.oneOf(Gen.choose(-100, 0), Gen.choose(100, 200))
  } yield coupon.copy(discountPercent = invalidDiscount)

  def subtotal(scenario: PricingScenario): BigDecimal =
    scenario.expectedItems.map(item => item.unitPrice * item.value.quantity).sum

  def discountAmount(subtotal: BigDecimal, discountPercent: Int): BigDecimal =
    subtotal * BigDecimal(discountPercent) / 100
}

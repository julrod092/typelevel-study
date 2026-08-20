package com.example.generators

import cats.Show
import com.amazonaws.dynamodb.{AttributeName, AttributeValue, BooleanAttributeValue, TableArn}
import com.example.domain.models.OrderStatus
import com.example.domain.repositories.*
import org.scalacheck.Gen

object RecordGenerators {

  given Show[LineItemRecord] = Show.fromToString
  given Show[CustomerRecord] = Show.fromToString
  given Show[CouponRecord] = Show.fromToString
  given Show[OrderRecord] = Show.fromToString
  given Show[TableArn] = Show.show(_.value)
  given Show[Throwable] =
    Show.show(error => Option(error.getMessage).getOrElse(error.getClass.getName))

  val lineItemRecord: Gen[LineItemRecord] = for {
    generatedSku <- DomainGenerators.nonEmptyString
    quantity <- DomainGenerators.customerLineItem.map(_.quantity)
    unitPrice <- DomainGenerators.positiveAmount
  } yield LineItemRecord(generatedSku, quantity, unitPrice, unitPrice * quantity)

  val customerRecord: Gen[CustomerRecord] = for {
    generatedCustomerId <- DomainGenerators.nonEmptyString
    tier <- Gen.oneOf(com.example.domain.models.CustomerTier.values.toSeq)
    name <- Gen.option(DomainGenerators.nonEmptyString)
    createdAt <- DomainGenerators.instant
  } yield CustomerRecord(generatedCustomerId, tier.toString, name, createdAt.toString)

  val customerRecordWithName: Gen[CustomerRecord] = for {
    record <- customerRecord
    name <- DomainGenerators.nonEmptyString
  } yield record.copy(name = Some(name))

  val customerRecordWithoutName: Gen[CustomerRecord] = customerRecord.map(_.copy(name = None))

  val couponRecord: Gen[CouponRecord] = for {
    code <- DomainGenerators.nonEmptyString
    discountPercent <- Gen.choose(1, 99)
    minOrderAmount <- DomainGenerators.positiveAmount
    usageLimit <- Gen.choose(1, 1000)
    usageCount <- Gen.choose(0, usageLimit)
    expiresAt <- DomainGenerators.instant
    stackableWithTiers <- Gen.oneOf(true, false)
  } yield CouponRecord(
    code,
    discountPercent,
    minOrderAmount,
    usageLimit,
    usageCount,
    expiresAt.toString,
    stackableWithTiers
  )

  val orderRecord: Gen[OrderRecord] = for {
    generatedOrderId <- DomainGenerators.nonEmptyString
    generatedCustomerId <- DomainGenerators.nonEmptyString
    status <- Gen.oneOf(OrderStatus.values.toSeq)
    itemCount <- Gen.choose(0, 8)
    items <- Gen.listOfN(itemCount, lineItemRecord)
    subtotal = items.map(_.lineTotal).sum
    discountRate <- Gen.choose(0, 100)
    discountAmount = subtotal * discountRate / 100
    couponCode <- Gen.option(DomainGenerators.nonEmptyString)
    createdAt <- DomainGenerators.instant
    updateOffset <- Gen.choose(0L, 86400L)
  } yield OrderRecord(
    generatedOrderId,
    generatedCustomerId,
    status.toString,
    items,
    subtotal,
    discountAmount,
    subtotal - discountAmount,
    couponCode,
    createdAt.toString,
    createdAt.plusSeconds(updateOffset).toString
  )

  val orderRecordWithCoupon: Gen[OrderRecord] = for {
    record <- orderRecord
    couponCode <- DomainGenerators.nonEmptyString
  } yield record.copy(couponCode = Some(couponCode))

  val orderRecordWithoutCoupon: Gen[OrderRecord] = orderRecord.map(_.copy(couponCode = None))

  val nonEmptyOrderRecord: Gen[OrderRecord] = for {
    record <- orderRecord
    itemCount <- Gen.choose(1, 8)
    items <- Gen.listOfN(itemCount, lineItemRecord)
    subtotal = items.map(_.lineTotal).sum
  } yield record.copy(
    items = items,
    subtotal = subtotal,
    discountAmount = BigDecimal(0),
    total = subtotal
  )

  val tableArn: Gen[TableArn] = DomainGenerators.nonEmptyString.map(TableArn.apply)

  val malformedNumber: Gen[String] =
    DomainGenerators.nonEmptyString.map(value => s"not-a-number-$value")

  val failure: Gen[Throwable] = DomainGenerators.nonEmptyString.map(new RuntimeException(_))

  def expectedAttributes(record: Product): Set[AttributeName] =
    record.productElementNames
      .zip(record.productIterator)
      .collect {
        case (name, value: Option[?]) if value.nonEmpty      => AttributeName(name)
        case (name, value) if !value.isInstanceOf[Option[?]] => AttributeName(name)
      }
      .toSet

  def wrongType(value: AttributeValue): AttributeValue =
    if (value.project.l.isDefined) AttributeValue.bool(BooleanAttributeValue(false))
    else AttributeValue.l(Nil)
}

package com.example.infrastructure.database

import cats.effect.Async
import cats.syntax.all.*
import com.amazonaws.dynamodb.*
import com.example.domain.models.Coupon.CouponCode
import com.example.domain.repositories.{CouponRecord, CouponsRepository}

import scala.util.Try

final case class DynamoCouponRepository[F[_]: Async](client: DynamoDB[F], tableName: TableArn)
    extends CouponsRepository[F]
    with BaseDynamoDB[CouponRecord] {

  override val keyAttribute: AttributeName = AttributeName("couponCode")

  override def encodeRecord(record: CouponRecord): DynamoRecord = Map(
    keyAttribute -> AttributeValue.s(StringAttributeValue(record.code)),
    AttributeName("discountPercent") -> AttributeValue.n(
      NumberAttributeValue(record.discountPercent.toString)
    ),
    AttributeName("minOrderAmount") -> AttributeValue.n(
      NumberAttributeValue(record.minOrderAmount.toString)
    ),
    AttributeName("usageLimit") -> AttributeValue.n(
      NumberAttributeValue(record.usageLimit.toString)
    ),
    AttributeName("usageCount") -> AttributeValue.n(
      NumberAttributeValue(record.usageCount.toString)
    ),
    AttributeName("expiresAt") -> AttributeValue.s(StringAttributeValue(record.expiresAt)),
    AttributeName("stackableWithTier") -> AttributeValue.bool(
      BooleanAttributeValue(record.stackableWithTiers)
    )
  )

  override def decodeRecord(record: DynamoRecord): Option[CouponRecord] = {
    for {
      code <- record.get(keyAttribute).flatMap(_.project.s).map(_.value)
      percentage <- record
        .get(AttributeName("discountPercent"))
        .flatMap(_.project.n)
        .flatMap(_.value.toIntOption)
      minOrderAmount <- record
        .get(AttributeName("minOrderAmount"))
        .flatMap(_.project.n)
        .flatMap(value => Try(BigDecimal(value.value)).toOption)
      limit <- record
        .get(AttributeName("usageLimit"))
        .flatMap(_.project.n)
        .flatMap(_.value.toIntOption)
      count <- record
        .get(AttributeName("usageCount"))
        .flatMap(_.project.n)
        .flatMap(_.value.toIntOption)
      expiration <- record.get(AttributeName("expiresAt")).flatMap(_.project.s).map(_.value)
      stackableWithTier <- record
        .get(AttributeName("stackableWithTier"))
        .flatMap(_.project.bool)
        .map(_.value)
    } yield CouponRecord(
      code = code,
      discountPercent = percentage,
      minOrderAmount = minOrderAmount,
      usageLimit = limit,
      usageCount = count,
      expiresAt = expiration,
      stackableWithTiers = stackableWithTier
    )
  }

  override def couponByCouponCode(code: CouponCode): F[Option[CouponRecord]] = {
    val key = Map(
      keyAttribute -> AttributeValue.s(StringAttributeValue(code.value))
    )

    client.getItem(tableName, key).map(_.item.flatMap(decodeRecord))
  }
}

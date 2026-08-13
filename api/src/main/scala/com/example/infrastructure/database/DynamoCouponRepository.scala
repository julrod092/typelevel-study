package com.example.infrastructure.database

import cats.effect.Async
import cats.syntax.all.*
import com.amazonaws.dynamodb.{
  AttributeName,
  AttributeValue,
  DynamoDB,
  StringAttributeValue,
  TableArn
}
import com.example.domain.models.Coupon.CouponCode
import com.example.domain.repositories.{
  CouponRecord,
  CouponsRepository,
  CustomerRecord,
  CustomersRepository
}
import com.example.infrastructure.configuration.AppConfig

final case class DynamoCouponRepository[F[_]: Async](client: DynamoDB[F], tableName: TableArn)
    extends CouponsRepository[F]
    with BaseDynamoDB[CouponRecord] {

  override def encodeRecord(record: CouponRecord): DynamoRecord = ???

  override def decodeRecord(record: DynamoRecord): Option[CouponRecord] = {
    for {
      code <- record.get(AttributeName("couponCode")).flatMap(_.project.s).map(_.value)
      percentage <- record
        .get(AttributeName("discountPercentage"))
        .flatMap(_.project.n)
        .map(_.value)
      minOrderAmount <- record
        .get(AttributeName("minOrderAmount"))
        .flatMap(_.project.n)
        .map(_.value)
      limit <- record.get(AttributeName("usageLimit")).flatMap(_.project.n).map(_.value)
      count <- record.get(AttributeName("usageCount")).flatMap(_.project.n).map(_.value)
      expiration <- record.get(AttributeName("expiresAt")).flatMap(_.project.s).map(_.value)
      stackableWithTier <- record
        .get(AttributeName("stackableWithTier"))
        .flatMap(_.project.bool)
        .map(_.value)
    } yield CouponRecord(
      code = code,
      discountAmount = BigDecimal(percentage),
      minOrderAmount = BigDecimal(minOrderAmount),
      usageLimit = limit.toInt,
      usageCount = count.toInt,
      expiresAt = expiration,
      stackableWithTiers = stackableWithTier
    )
  }

  override def couponByCouponCode(code: CouponCode): F[Option[CouponRecord]] = {
    val key = Map(
      AttributeName("couponCode") -> AttributeValue.s(StringAttributeValue(code.value))
    )

    client
      .getItem(tableName, key)
      .attempt
      .map(_.toOption.flatMap(_.item.flatMap(decodeRecord)))
  }
}

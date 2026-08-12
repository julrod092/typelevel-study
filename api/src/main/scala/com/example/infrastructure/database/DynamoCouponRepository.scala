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

final case class DynamoCouponRepository[F[_]: Async](client: DynamoDB[F])
    extends CouponsRepository[F] {

  private val tableName = TableArn("Coupon")

  private def decodeResponse(
      response: Map[AttributeName, AttributeValue]
  ): Option[CouponRecord] = {
    for {
      code <- response.get(AttributeName("couponCode")).flatMap(_.project.s).map(_.value)
      percentage <- response
        .get(AttributeName("discountPercentage"))
        .flatMap(_.project.n)
        .map(_.value)
      minOrderAmount <- response
        .get(AttributeName("minOrderAmount"))
        .flatMap(_.project.n)
        .map(_.value)
      limit <- response.get(AttributeName("usageLimit")).flatMap(_.project.n).map(_.value)
      count <- response.get(AttributeName("usageCount")).flatMap(_.project.n).map(_.value)
      expiration <- response.get(AttributeName("expiresAt")).flatMap(_.project.s).map(_.value)
      stackableWithTier <- response
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
      .map(_.toOption.flatMap(_.item.flatMap(decodeResponse)))
  }
}

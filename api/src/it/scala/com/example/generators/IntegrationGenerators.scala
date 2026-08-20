package com.example.generators

import cats.Show
import com.amazonaws.dynamodb.TableArn
import com.example.domain.models.{Coupon, Customer}
import org.scalacheck.Gen

object IntegrationGenerators {
  given Show[Customer.CustomerId] = Show.show(_.value)
  given Show[Coupon.CouponCode] = Show.show(_.value)

  val missingCustomerId: Gen[Customer.CustomerId] =
    DomainGenerators.nonEmptyString.map(value => Customer.CustomerId(s"missing-$value"))

  val missingCouponCode: Gen[Coupon.CouponCode] =
    DomainGenerators.nonEmptyString.map(value => Coupon.CouponCode(s"missing-$value"))

  val missingTable: Gen[TableArn] =
    DomainGenerators.nonEmptyString.map(value => TableArn(s"missing-$value"))
}

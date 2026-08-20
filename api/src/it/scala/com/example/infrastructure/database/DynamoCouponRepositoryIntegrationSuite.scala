package com.example.infrastructure.database

import cats.syntax.all.*
import com.amazonaws.dynamodb.{AttributeValue, StringAttributeValue}
import com.example.domain.models.Coupon
import com.example.generators.{IntegrationGenerators, RecordGenerators}
import com.example.generators.IntegrationGenerators.given
import com.example.generators.RecordGenerators.given
import com.example.support.{DynamoIntegrationSuite, DynamoTestSupport}

object DynamoCouponRepositoryIntegrationSuite extends DynamoIntegrationSuite {

  test("generated coupons can be seeded and retrieved") { fixture =>
    forall(RecordGenerators.couponRecord) { record =>
      val encoded = fixture.coupons.encodeRecord(record)
      val key = Map(
        DynamoSchema.CouponCode -> AttributeValue.s(StringAttributeValue(record.code))
      )

      for {
        _ <- fixture.client.putItem(fixture.tables.couponsTableName, encoded)
        retrieved <- fixture.coupons.couponByCouponCode(Coupon.CouponCode(record.code))
        raw <- DynamoTestSupport.getRawItem(
          fixture.client,
          fixture.tables.couponsTableName,
          key
        )
      } yield expect(retrieved.contains(record)) and
        expect(raw.flatMap(fixture.coupons.decodeRecord).contains(record)) and
        expect(raw.exists(_.keySet == encoded.keySet))
    }
  }

  test("generated missing coupon codes return none") { fixture =>
    forall(IntegrationGenerators.missingCouponCode) { code =>
      fixture.coupons.couponByCouponCode(code).map(result => expect(result.isEmpty))
    }
  }

  test("malformed stored coupons do not decode") { fixture =>
    forall(RecordGenerators.couponRecord) { record =>
      val encoded = fixture.coupons.encodeRecord(record)
      val removableKey = encoded.keys.filterNot(_ == DynamoSchema.CouponCode).head
      val malformed = encoded - removableKey

      fixture.client
        .putItem(fixture.tables.couponsTableName, malformed)
        .void *> fixture.coupons
        .couponByCouponCode(Coupon.CouponCode(record.code))
        .map(result => expect(result.isEmpty))
    }
  }
}

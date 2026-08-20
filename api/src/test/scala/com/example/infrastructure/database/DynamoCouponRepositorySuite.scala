package com.example.infrastructure.database

import cats.effect.IO
import com.amazonaws.dynamodb.{AttributeValue, NumberAttributeValue}
import com.example.generators.RecordGenerators
import com.example.generators.RecordGenerators.given
import com.example.support.DynamoClientStub
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object DynamoCouponRepositorySuite extends SimpleIOSuite with Checkers {

  test("coupon records round trip through DynamoDB attributes") {
    val scenario = for {
      record <- RecordGenerators.couponRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoCouponRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)

      expect(encoded.size == record.productArity) and
        expect(repository.decodeRecord(encoded).contains(record))
    }
  }

  test("every coupon attribute is mandatory and type checked") {
    val scenario = for {
      record <- RecordGenerators.couponRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoCouponRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)

      expect(encoded.keys.forall(key => repository.decodeRecord(encoded - key).isEmpty)) and
        expect(
          encoded.forall { case (key, value) =>
            repository.decodeRecord(encoded.updated(key, RecordGenerators.wrongType(value))).isEmpty
          }
        )
    }
  }

  test("malformed coupon numbers fail decoding") {
    val scenario = for {
      record <- RecordGenerators.couponRecord
      table <- RecordGenerators.tableArn
      malformed <- RecordGenerators.malformedNumber
    } yield (record, table, malformed)

    forall(scenario) { case (record, table, malformed) =>
      val repository = DynamoCouponRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)
      val numericKeys = encoded.collect { case (key, value) if value.project.n.isDefined => key }

      expect(
        numericKeys.forall(key =>
          repository
            .decodeRecord(
              encoded.updated(key, AttributeValue.n(NumberAttributeValue(malformed)))
            )
            .isEmpty
        )
      )
    }
  }
}

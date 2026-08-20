package com.example.infrastructure.database

import cats.effect.IO
import cats.syntax.all.*
import com.amazonaws.dynamodb.{GetItemOutput, PutItemOutput}
import com.example.domain.models.{Coupon, Customer}
import com.example.generators.RecordGenerators
import com.example.generators.RecordGenerators.given
import com.example.support.DynamoClientStub
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object DynamoRepositorySemanticsSuite extends SimpleIOSuite with Checkers {

  test("an existing customer is decoded") {
    val scenario = for {
      record <- RecordGenerators.customerRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val encoder = DynamoCustomerRepository[IO](DynamoClientStub.unexpected, table)
      val client = DynamoClientStub(getItemResult = (_, _) =>
        IO.pure(GetItemOutput(item = Some(encoder.encodeRecord(record))))
      )
      val repository = DynamoCustomerRepository[IO](client, table)

      repository
        .customerByCustomerId(Customer.CustomerId(record.customerId))
        .map(result => expect(result.contains(record)))
    }
  }

  test("a missing customer returns none") {
    val scenario = for {
      customerId <- RecordGenerators.customerRecord.map(_.customerId)
      table <- RecordGenerators.tableArn
    } yield (customerId, table)

    forall(scenario) { case (customerId, table) =>
      val client = DynamoClientStub(getItemResult = (_, _) => IO.pure(GetItemOutput()))
      val repository = DynamoCustomerRepository[IO](client, table)

      repository
        .customerByCustomerId(Customer.CustomerId(customerId))
        .map(result => expect(result.isEmpty))
    }
  }

  test("customer read failures remain failures") {
    val scenario = for {
      customerId <- RecordGenerators.customerRecord.map(_.customerId)
      table <- RecordGenerators.tableArn
      error <- RecordGenerators.failure
    } yield (customerId, table, error)

    forall(scenario) { case (customerId, table, error) =>
      val client = DynamoClientStub(getItemResult = (_, _) => IO.raiseError(error))
      val repository = DynamoCustomerRepository[IO](client, table)

      repository
        .customerByCustomerId(Customer.CustomerId(customerId))
        .attempt
        .map(result => expect(result.left.toOption.contains(error)))
    }
  }

  test("an existing coupon is decoded") {
    val scenario = for {
      record <- RecordGenerators.couponRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val encoder = DynamoCouponRepository[IO](DynamoClientStub.unexpected, table)
      val client = DynamoClientStub(getItemResult = (_, _) =>
        IO.pure(GetItemOutput(item = Some(encoder.encodeRecord(record))))
      )
      val repository = DynamoCouponRepository[IO](client, table)

      repository
        .couponByCouponCode(Coupon.CouponCode(record.code))
        .map(result => expect(result.contains(record)))
    }
  }

  test("a missing coupon returns none") {
    val scenario = for {
      code <- RecordGenerators.couponRecord.map(_.code)
      table <- RecordGenerators.tableArn
    } yield (code, table)

    forall(scenario) { case (code, table) =>
      val client = DynamoClientStub(getItemResult = (_, _) => IO.pure(GetItemOutput()))
      val repository = DynamoCouponRepository[IO](client, table)

      repository
        .couponByCouponCode(Coupon.CouponCode(code))
        .map(result => expect(result.isEmpty))
    }
  }

  test("coupon read failures remain failures") {
    val scenario = for {
      code <- RecordGenerators.couponRecord.map(_.code)
      table <- RecordGenerators.tableArn
      error <- RecordGenerators.failure
    } yield (code, table, error)

    forall(scenario) { case (code, table, error) =>
      val client = DynamoClientStub(getItemResult = (_, _) => IO.raiseError(error))
      val repository = DynamoCouponRepository[IO](client, table)

      repository
        .couponByCouponCode(Coupon.CouponCode(code))
        .attempt
        .map(result => expect(result.left.toOption.contains(error)))
    }
  }

  test("a successful order write returns the record") {
    val scenario = for {
      record <- RecordGenerators.orderRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val client = DynamoClientStub(putItemResult = (_, _) => IO.pure(PutItemOutput()))
      val repository = DynamoOrderRepository[IO](client, table)

      repository.savePricedOrder(record).map(result => expect(result == record))
    }
  }

  test("order write failures remain failures") {
    val scenario = for {
      record <- RecordGenerators.orderRecord
      table <- RecordGenerators.tableArn
      error <- RecordGenerators.failure
    } yield (record, table, error)

    forall(scenario) { case (record, table, error) =>
      val client = DynamoClientStub(putItemResult = (_, _) => IO.raiseError(error))
      val repository = DynamoOrderRepository[IO](client, table)

      repository
        .savePricedOrder(record)
        .attempt
        .map(result => expect(result.left.toOption.contains(error)))
    }
  }
}

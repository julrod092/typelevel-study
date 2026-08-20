package com.example.infrastructure.database

import cats.effect.IO
import com.example.generators.RecordGenerators
import com.example.generators.RecordGenerators.given
import com.example.support.DynamoClientStub
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object DynamoCustomerRepositorySuite extends SimpleIOSuite with Checkers {

  test("customer records round trip through DynamoDB attributes") {
    val scenario = for {
      record <- RecordGenerators.customerRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoCustomerRepository[IO](DynamoClientStub.unexpected, table)

      expect(repository.decodeRecord(repository.encodeRecord(record)).contains(record))
    }
  }

  test("an absent optional customer name is omitted and decodes") {
    val scenario = for {
      record <- RecordGenerators.customerRecordWithoutName
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoCustomerRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)

      expect(encoded.keySet == RecordGenerators.expectedAttributes(record)) and
        expect(repository.decodeRecord(encoded).contains(record))
    }
  }

  test("a present optional customer name has to be a string") {
    val scenario = for {
      record <- RecordGenerators.customerRecordWithName
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoCustomerRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)
      val optionalKey = (encoded.keySet -- RecordGenerators.expectedAttributes(record.copy(name = None))).head

      expect(repository.decodeRecord(encoded.updated(optionalKey, RecordGenerators.wrongType(encoded(optionalKey)))).isEmpty)
    }
  }

  test("missing or wrongly typed mandatory customer attributes fail decoding") {
    val scenario = for {
      record <- RecordGenerators.customerRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoCustomerRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)
      val mandatoryKeys = RecordGenerators.expectedAttributes(record.copy(name = None))

      expect(mandatoryKeys.forall(key => repository.decodeRecord(encoded - key).isEmpty)) and
        expect(
          mandatoryKeys.forall(key =>
            repository
              .decodeRecord(encoded.updated(key, RecordGenerators.wrongType(encoded(key))))
              .isEmpty
          )
        )
    }
  }
}

package com.example.infrastructure.database

import cats.effect.IO
import com.amazonaws.dynamodb.{AttributeName, AttributeValue, NumberAttributeValue}
import com.example.generators.RecordGenerators
import com.example.generators.RecordGenerators.given
import com.example.support.DynamoClientStub
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object DynamoOrderRepositorySuite extends SimpleIOSuite with Checkers {

  test("order records round trip through DynamoDB attributes") {
    val scenario = for {
      record <- RecordGenerators.orderRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoOrderRepository[IO](DynamoClientStub.unexpected, table)

      expect(repository.decodeRecord(repository.encodeRecord(record)).contains(record))
    }
  }

  test("every represented order field is a top-level attribute") {
    val scenario = for {
      record <- RecordGenerators.orderRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoOrderRepository[IO](DynamoClientStub.unexpected, table)

      expect(repository.encodeRecord(record).keySet == RecordGenerators.expectedAttributes(record))
    }
  }

  test("order items are stored as a list of maps") {
    val scenario = for {
      record <- RecordGenerators.nonEmptyOrderRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoOrderRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)
      val listAttribute = encoded.values.flatMap(_.project.l).toList.headOption

      expect(listAttribute.exists(_.size == record.items.size)) and
        expect(listAttribute.exists(_.forall(_.project.m.isDefined)))
    }
  }

  test("missing or wrongly typed mandatory order attributes fail decoding") {
    val scenario = for {
      record <- RecordGenerators.orderRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoOrderRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)
      val mandatoryKeys = RecordGenerators.expectedAttributes(record.copy(couponCode = None))

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

  test("malformed top-level order numbers fail decoding") {
    val scenario = for {
      record <- RecordGenerators.orderRecord
      table <- RecordGenerators.tableArn
      malformed <- RecordGenerators.malformedNumber
    } yield (record, table, malformed)

    forall(scenario) { case (record, table, malformed) =>
      val repository = DynamoOrderRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)
      val numericKeys = encoded.collect { case (key, value) if value.project.n.isDefined => key }

      expect(
        numericKeys.forall(key =>
          repository
            .decodeRecord(encoded.updated(key, AttributeValue.n(NumberAttributeValue(malformed))))
            .isEmpty
        )
      )
    }
  }

  test("a malformed nested order item fails the complete decode") {
    val scenario = for {
      record <- RecordGenerators.nonEmptyOrderRecord
      table <- RecordGenerators.tableArn
    } yield (record, table)

    forall(scenario) { case (record, table) =>
      val repository = DynamoOrderRepository[IO](DynamoClientStub.unexpected, table)
      val encoded = repository.encodeRecord(record)
      val (itemsKey, items) = encoded.collectFirst { case (key, value) if value.project.l.isDefined =>
        key -> value.project.l.get
      }.get
      val firstItem = items.head.project.m.get
      val malformedItem = AttributeValue.m(firstItem - firstItem.keys.head)
      val malformedItems = encoded.updated(itemsKey, AttributeValue.l(malformedItem :: items.tail))

      expect(repository.decodeRecord(malformedItems).isEmpty)
    }
  }
}

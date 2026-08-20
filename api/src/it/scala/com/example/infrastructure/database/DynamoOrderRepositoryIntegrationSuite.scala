package com.example.infrastructure.database

import cats.syntax.all.*
import com.amazonaws.dynamodb.{AttributeValue, StringAttributeValue}
import com.example.generators.{IntegrationGenerators, RecordGenerators}
import com.example.generators.RecordGenerators.given
import com.example.support.{DynamoIntegrationSuite, DynamoTestSupport}

object DynamoOrderRepositoryIntegrationSuite extends DynamoIntegrationSuite {

  test("generated orders are stored as top-level DynamoDB attributes") { fixture =>
    forall(RecordGenerators.orderRecord) { record =>
      val expected = fixture.orders.encodeRecord(record)
      val key = Map(
        fixture.orders.keyAttribute -> AttributeValue.s(StringAttributeValue(record.orderId))
      )

      for {
        saved <- fixture.orders.savePricedOrder(record)
        raw <- DynamoTestSupport.getRawItem(
          fixture.client,
          fixture.tables.ordersTableName,
          key
        )
      } yield expect(saved == record) and
        expect(raw.flatMap(fixture.orders.decodeRecord).contains(record)) and
        expect(raw.exists(_.keySet == RecordGenerators.expectedAttributes(record))) and
        expect(
          raw.exists(_.values.flatMap(_.project.l).forall(_.forall(_.project.m.isDefined)))
        )
    }
  }

  test("orders without coupons omit only the absent field") { fixture =>
    forall(RecordGenerators.orderRecordWithoutCoupon) { record =>
      val expected = fixture.orders.encodeRecord(record)

      fixture.orders
        .savePricedOrder(record)
        .map(_ => expect(expected.keySet == RecordGenerators.expectedAttributes(record)))
    }
  }

  test("orders with coupons represent every record field") { fixture =>
    forall(RecordGenerators.orderRecordWithCoupon) { record =>
      val expected = fixture.orders.encodeRecord(record)

      fixture.orders
        .savePricedOrder(record)
        .map(_ => expect(expected.keySet == RecordGenerators.expectedAttributes(record)))
    }
  }

  test("writes to generated missing tables remain failures") { fixture =>
    val scenario = for {
      table <- IntegrationGenerators.missingTable
      record <- RecordGenerators.orderRecord
    } yield (table, record)

    forall(scenario) { case (table, record) =>
      DynamoOrderRepository(fixture.client, table)
        .savePricedOrder(record)
        .attempt
        .map(result => expect(result.isLeft))
    }
  }
}

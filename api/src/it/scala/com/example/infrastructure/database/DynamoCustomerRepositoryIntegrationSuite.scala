package com.example.infrastructure.database

import cats.effect.IO
import cats.syntax.all.*
import com.amazonaws.dynamodb.{AttributeValue, StringAttributeValue}
import com.example.generators.{IntegrationGenerators, RecordGenerators}
import com.example.generators.IntegrationGenerators.given
import com.example.generators.RecordGenerators.given
import com.example.support.{DynamoIntegrationSuite, DynamoTestSupport}

object DynamoCustomerRepositoryIntegrationSuite extends DynamoIntegrationSuite {

  test("generated customers can be seeded and retrieved") { fixture =>
    forall(RecordGenerators.customerRecord) { record =>
      val encoded = fixture.customers.encodeRecord(record)
      val key = Map(
        DynamoSchema.CustomerId -> AttributeValue.s(StringAttributeValue(record.customerId))
      )

      for {
        _ <- fixture.client.putItem(fixture.tables.customersTableName, encoded)
        retrieved <- fixture.customers.customerByCustomerId(
          com.example.domain.models.Customer.CustomerId(record.customerId)
        )
        raw <- DynamoTestSupport.getRawItem(
          fixture.client,
          fixture.tables.customersTableName,
          key
        )
      } yield expect(retrieved.contains(record)) and expect(raw.contains(encoded))
    }
  }

  test("customers without names retain the absent attribute") { fixture =>
    forall(RecordGenerators.customerRecordWithoutName) { record =>
      val encoded = fixture.customers.encodeRecord(record)

      fixture.client
        .putItem(fixture.tables.customersTableName, encoded)
        .void *> fixture.customers
        .customerByCustomerId(com.example.domain.models.Customer.CustomerId(record.customerId))
        .map(result =>
          expect(encoded.keySet == RecordGenerators.expectedAttributes(record)) and
            expect(result.contains(record))
        )
    }
  }

  test("customers with names retain the represented attribute") { fixture =>
    forall(RecordGenerators.customerRecordWithName) { record =>
      val encoded = fixture.customers.encodeRecord(record)

      fixture.client
        .putItem(fixture.tables.customersTableName, encoded)
        .void *> fixture.customers
        .customerByCustomerId(com.example.domain.models.Customer.CustomerId(record.customerId))
        .map(result =>
          expect(encoded.keySet == RecordGenerators.expectedAttributes(record)) and
            expect(result.contains(record))
        )
    }
  }

  test("generated missing customer ids return none") { fixture =>
    forall(IntegrationGenerators.missingCustomerId) { customerId =>
      fixture.customers.customerByCustomerId(customerId).map(result => expect(result.isEmpty))
    }
  }
}

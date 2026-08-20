package com.example.infrastructure.database

import cats.effect.Async
import cats.syntax.all.*
import com.amazonaws.dynamodb.*
import com.example.domain.models.Customer.CustomerId
import com.example.domain.repositories.{CustomerRecord, CustomersRepository}

final case class DynamoCustomerRepository[F[_]: Async](client: DynamoDB[F], tableName: TableArn)
    extends CustomersRepository[F]
    with BaseDynamoDB[CustomerRecord] {

  override val keyAttribute: AttributeName = AttributeName("customerId")

  override def encodeRecord(record: CustomerRecord): DynamoRecord = {
    val mandatoryValues = Map(
      keyAttribute -> AttributeValue.s(StringAttributeValue(record.customerId)),
      AttributeName("tier") -> AttributeValue.s(StringAttributeValue(record.tier)),
      AttributeName("createdAt") -> AttributeValue.s(StringAttributeValue(record.createdAt))
    )

    record.name.fold(mandatoryValues) { name =>
      mandatoryValues.updated(
        AttributeName("name"),
        AttributeValue.s(StringAttributeValue(name))
      )
    }
  }

  override def decodeRecord(record: DynamoRecord): Option[CustomerRecord] = {
    for {
      customerId <- record.get(keyAttribute).flatMap(_.project.s).map(_.value)
      tier <- record.get(AttributeName("tier")).flatMap(_.project.s).map(_.value)
      name <- record
        .get(AttributeName("name"))
        .fold[Option[Option[String]]](Some(None))(
          _.project.s.map(value => Some(value.value))
        )
      createdAt <- record.get(AttributeName("createdAt")).flatMap(_.project.s).map(_.value)
    } yield CustomerRecord(
      customerId = customerId,
      tier = tier,
      name = name,
      createdAt = createdAt
    )
  }

  override def customerByCustomerId(customerId: CustomerId): F[Option[CustomerRecord]] = {
    val key = Map(
      keyAttribute -> AttributeValue.s(StringAttributeValue(customerId.value))
    )

    client.getItem(tableName, key).map(_.item.flatMap(decodeRecord))
  }
}

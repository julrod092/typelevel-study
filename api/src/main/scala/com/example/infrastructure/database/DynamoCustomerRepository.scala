package com.example.infrastructure.database

import cats.effect.Async
import cats.syntax.all.*
import com.amazonaws.dynamodb.*
import com.example.domain.models.Customer.CustomerId
import com.example.domain.repositories.{CustomerRecord, CustomersRepository}

final case class DynamoCustomerRepository[F[_]: Async](client: DynamoDB[F], tableName: TableArn)
    extends CustomersRepository[F]
    with BaseDynamoDB[CustomerRecord] {

  override def encodeRecord(record: CustomerRecord): DynamoRecord = ???

  override def decodeRecord(record: DynamoRecord): Option[CustomerRecord] = {
    for {
      customerId <- record.get(AttributeName("customerId")).flatMap(_.project.s).map(_.value)
      tier <- record.get(AttributeName("tier")).flatMap(_.project.s).map(_.value)
      name <- record.get(AttributeName("name")).map(_.project.s)
      createdAt <- record.get(AttributeName("createdAt")).flatMap(_.project.s).map(_.value)
    } yield CustomerRecord(
      customerId = customerId,
      tier = tier,
      name = name.map(_.value),
      createdAt = createdAt
    )
  }

  override def customerByCustomerId(customerId: CustomerId): F[Option[CustomerRecord]] = {
    val key = Map(
      AttributeName("customerId") -> AttributeValue.s(StringAttributeValue(customerId.value))
    )

    client
      .getItem(tableName, key)
      .attempt
      .map(_.toOption.flatMap(_.item.flatMap(decodeRecord)))
  }
}

package com.example.infrastructure.database

import cats.effect.Async
import cats.syntax.all.*
import com.amazonaws.dynamodb.*
import com.example.domain.repositories.CustomersRepository
import com.example.domain.repositories.CustomerRecord
import com.example.domain.models.Customer.CustomerId

final case class DynamoCustomerRepository[F[_]: Async](client: DynamoDB[F])
    extends CustomersRepository[F] {

  private val tableName = TableArn("Customer")

  private def decodeResponse(
      response: Map[AttributeName, AttributeValue]
  ): Option[CustomerRecord] = {
    for {
      customerId <- response.get(AttributeName("customerId")).flatMap(_.project.s).map(_.value)
      tier <- response.get(AttributeName("tier")).flatMap(_.project.s).map(_.value)
      name <- response.get(AttributeName("name")).map(_.project.s)
      createdAt <- response.get(AttributeName("createdAt")).flatMap(_.project.s).map(_.value)
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
      .map(_.toOption.flatMap(_.item.flatMap(decodeResponse)))
  }
}

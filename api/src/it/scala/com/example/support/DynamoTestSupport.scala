package com.example.support

import cats.Functor
import cats.effect.Temporal
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.amazonaws.dynamodb.*

import scala.concurrent.duration.*

object DynamoTestSupport {

  def createTable[F[_]: Temporal](
      client: DynamoDB[F],
      tableName: TableArn,
      partitionKey: String
  ): F[Unit] =
    client
      .createTable(
        tableName = tableName,
        attributeDefinitions = Some(
          List(
            AttributeDefinition(
              attributeName = KeySchemaAttributeName(partitionKey),
              attributeType = ScalarAttributeType.S
            )
          )
        ),
        keySchema = Some(
          List(
            KeySchemaElement(
              attributeName = KeySchemaAttributeName(partitionKey),
              keyType = KeyType.HASH
            )
          )
        ),
        billingMode = Some(BillingMode.PAY_PER_REQUEST)
      )
      .void *> waitUntilActive(client, tableName)

  def getRawItem[F[_]: Functor](
      client: DynamoDB[F],
      tableName: TableArn,
      key: Map[AttributeName, AttributeValue]
  ): F[Option[Map[AttributeName, AttributeValue]]] =
    client.getItem(tableName, key, consistentRead = Some(ConsistentRead(true))).map(_.item)

  def deleteTable[F[_]: Functor](client: DynamoDB[F], tableName: TableArn): F[Unit] =
    client.deleteTable(tableName).void

  private def waitUntilActive[F[_]: Temporal](
      client: DynamoDB[F],
      tableName: TableArn
  ): F[Unit] = {
    def poll: F[Unit] =
      client.describeTable(tableName).attempt.flatMap {
        case Right(output) if output.table.flatMap(_.tableStatus).contains(TableStatus.ACTIVE) =>
          Temporal[F].unit
        case Right(_) | Left(_: ResourceNotFoundException) =>
          Temporal[F].sleep(50.millis) *> poll
        case Left(error) => Temporal[F].raiseError(error)
      }

    poll.timeout(20.seconds)
  }
}

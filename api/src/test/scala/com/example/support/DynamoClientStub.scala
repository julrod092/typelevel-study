package com.example.support

import cats.effect.IO
import com.amazonaws.dynamodb.*

object DynamoClientStub {
  type Item = Map[AttributeName, AttributeValue]

  private val unexpectedOperation: IO[Nothing] =
    IO.raiseError(new AssertionError("Unexpected DynamoDB operation"))

  val unexpected: DynamoDB[IO] =
    new DynamoDBGen.Default[IO](unexpectedOperation) {}

  def apply(
      getItemResult: (TableArn, Item) => IO[GetItemOutput] = (_, _) => unexpectedOperation,
      putItemResult: (TableArn, Item) => IO[PutItemOutput] = (_, _) => unexpectedOperation
  ): DynamoDB[IO] =
    new DynamoDBGen.Default[IO](unexpectedOperation) {
      override def getItem(
          tableName: TableArn,
          key: Item,
          attributesToGet: Option[List[AttributeName]],
          consistentRead: Option[ConsistentRead],
          returnConsumedCapacity: Option[ReturnConsumedCapacity],
          projectionExpression: Option[ProjectionExpression],
          expressionAttributeNames: Option[Map[ExpressionAttributeNameVariable, AttributeName]]
      ): IO[GetItemOutput] = getItemResult(tableName, key)

      override def putItem(
          tableName: TableArn,
          item: Item,
          expected: Option[Map[AttributeName, ExpectedAttributeValue]],
          returnValues: Option[ReturnValue],
          returnConsumedCapacity: Option[ReturnConsumedCapacity],
          returnItemCollectionMetrics: Option[ReturnItemCollectionMetrics],
          conditionalOperator: Option[ConditionalOperator],
          conditionExpression: Option[ConditionExpression],
          expressionAttributeNames: Option[Map[ExpressionAttributeNameVariable, AttributeName]],
          expressionAttributeValues: Option[Map[ExpressionAttributeValueVariable, AttributeValue]],
          returnValuesOnConditionCheckFailure: Option[ReturnValuesOnConditionCheckFailure]
      ): IO[PutItemOutput] = putItemResult(tableName, item)
    }
}

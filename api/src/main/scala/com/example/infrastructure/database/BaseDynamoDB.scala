package com.example.infrastructure.database

import com.amazonaws.dynamodb.{AttributeName, AttributeValue}
import com.example.domain.repositories.RepositoryRecord

trait BaseDynamoDB[T <: RepositoryRecord] {

  type DynamoRecord = Map[AttributeName, AttributeValue]

  def encodeRecord(record: T): DynamoRecord
  def decodeRecord(record: DynamoRecord): Option[T]
}

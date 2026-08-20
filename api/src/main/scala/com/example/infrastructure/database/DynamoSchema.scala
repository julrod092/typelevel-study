package com.example.infrastructure.database

import com.amazonaws.dynamodb.AttributeName

object DynamoSchema {
  val CustomerId: AttributeName = AttributeName("customerId")
  val CouponCode: AttributeName = AttributeName("couponCode")
  val OrderId: AttributeName = AttributeName("orderId")
}

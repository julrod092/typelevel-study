package com.example.domain.transformers

import com.example.domain.models.{LineItem, Order, OrderStatus}
import com.example.domain.repositories.{LineItemRecord, OrderRecord}
import io.scalaland.chimney.Transformer

object RecordTransformer {
  
  given Transformer[LineItemRecord, LineItem] =
    Transformer.define[LineItemRecord, LineItem]
      .withFieldComputed(_.sku, value => LineItem.Sku(value.sku))
      .buildTransformer
  
  given Transformer[OrderRecord, Order] = 
    Transformer.define[OrderRecord, Order]
      .withFieldComputed(_.orderId, value => Order.OrderId(value.orderId))
      .withFieldComputed(_.status, value => OrderStatus.valueOf(value.status))
      

}

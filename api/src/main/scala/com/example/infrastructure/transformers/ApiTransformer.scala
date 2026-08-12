package com.example.infrastructure.transformers

import com.example.domain.models.{Customer, LineItem, Order, OrderStatus}
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.Transformer
import pricing.{LineItemDTO, OrderPriceDTO, OrderPricedDTO}

object ApiTransformer {

  given Transformer[LineItemDTO, LineItem] = Transformer.define[LineItemDTO, LineItem]
    .withFieldComputed(_.sku, value => LineItem.Sku(value.sku))
    .buildTransformer

  given Transformer[OrderPriceDTO, Order] =
    Transformer.define[OrderPriceDTO, Order]
      .withFieldComputed(_.orderId, value => Order.OrderId(value.o))
      .withFieldComputed(_.customerId, value => Customer.CustomerId(value.customerId))
      .withFieldComputed(_.status, value => OrderStatus.valueOf(value.status))
      .buildTransformer


}

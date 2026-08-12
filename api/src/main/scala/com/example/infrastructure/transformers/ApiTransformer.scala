package com.example.infrastructure.transformers

import com.example.domain.models.{Customer, LineItem, Order, OrderStatus, CustomerOrder, CustomerLineItem}
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.Transformer
import pricing.{LineItemDTO, OrderPriceDTO, OrderPricedDTO}

object ApiTransformer {

  given Transformer[LineItemDTO, CustomerLineItem] =
    Transformer.define[LineItemDTO, CustomerLineItem]
      .withFieldComputed(_.sku, value => LineItem.Sku(value.sku))
      .buildTransformer
}

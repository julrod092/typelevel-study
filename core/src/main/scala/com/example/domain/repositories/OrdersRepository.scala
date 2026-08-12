package com.example.domain.repositories

import cats.data.EitherT
import cats.effect.Async
import com.example.domain.models.Customer.CustomerId

trait OrdersRepository[F[_] : Async] {

  def savePricedOrder(value: OrderRecord): EitherT[F, String, OrderRecord]
  def listPricedOrdersByCustomerId(value: CustomerId): EitherT[F, String, List[OrderRecord]]
}

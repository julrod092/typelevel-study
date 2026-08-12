package com.example.domain.repositories

import cats.data.EitherT
import cats.effect.Async
import com.example.domain.models.Customer.CustomerId
import com.example.domain.models.PricingError

trait OrdersRepository[F[_]: Async] {

  def savePricedOrder(value: OrderRecord): EitherT[F, PricingError, OrderRecord]
  def listPricedOrdersByCustomerId(value: CustomerId): EitherT[F, PricingError, List[OrderRecord]]
}

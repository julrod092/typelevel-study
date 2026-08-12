package com.example.domain.repositories

import cats.data.EitherT
import cats.effect.Async
import com.example.domain.models.Customer.CustomerId
import com.example.domain.models.DomainError

trait OrdersRepository[F[_]] {

  def savePricedOrder(value: OrderRecord): F[OrderRecord]
}

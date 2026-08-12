package com.example.infrastructure.routes

import cats.effect.Async
import pricing.*
import smithy4s.kinds.FunctorAlgebra

object PriceRoutes {

  def impl[F[_]: Async]: FunctorAlgebra[PriceAPIGen, F] = new PriceAPI[F] {
    /** HTTP POST /orders/price */
    override def getPrice(input: OrderPriceDTO): F[OrderPricedDTO] = ???
  }
}

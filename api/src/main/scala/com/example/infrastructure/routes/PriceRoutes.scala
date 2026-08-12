package com.example.infrastructure.routes

import cats.effect.Async
import pricing.*
import smithy4s.kinds.FunctorAlgebra

object PriceRoutes extends Routes {

  def impl[F[_]: Async]: FunctorAlgebra[PriceAPIGen, F] = new PriceAPI[F] {

    /** HTTP POST /orders/price */
    override def orderPricing(input: OrderPriceDTO): F[OrderPricedDTO] = ???
  }
}

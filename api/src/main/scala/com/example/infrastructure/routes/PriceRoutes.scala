package com.example.infrastructure.routes

import cats.data.EitherT
import cats.effect.Async
import pricing.*
import pricing.PriceAPIOperation.GetPriceError
import smithy4s.kinds.FunctorAlgebra

object PriceRoutes extends Routes {
  
  def impl[F[_]: Async]: FunctorAlgebra[PriceAPIGen, F] = new PriceAPI[F] {
    /** HTTP POST /orders/price */
    override def getPrice(input: OrderPriceDTO): Result[F, OrderPricedDTO] = ???
  }
}

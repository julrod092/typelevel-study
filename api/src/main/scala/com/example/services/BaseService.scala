package com.example.services

import cats.data.{EitherT, Kleisli}
import com.example.infrastructure.configuration.PricingEnvironment
import pricing.PriceAPIOperation.OrderPricingError

trait BaseService {

  type Response[F[_], T] = EitherT[F, OrderPricingError, T]

  type Program[F[_], T] = Kleisli[[X] =>> Response[F, X], PricingEnvironment[F], T]

}

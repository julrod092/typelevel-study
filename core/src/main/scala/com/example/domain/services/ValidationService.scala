package com.example.domain.services

import cats.data.EitherT
import com.example.domain.models.CustomerOrder
import com.example.domain.models.PrincingError
import com.example.domain.models.Order
import cats.effect.Async

object ValidationService {

  def validate[F[_]: Async](customerOrder: CustomerOrder): EitherT[F, PrincingError, Order] = ???
}

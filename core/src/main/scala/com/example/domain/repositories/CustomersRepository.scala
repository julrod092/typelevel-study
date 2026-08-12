package com.example.domain.repositories

import cats.data.EitherT
import cats.effect.Async
import com.example.domain.models.Customer.CustomerId

trait CustomersRepository[F[_] : Async] {

  def customerByCustomerId(value: CustomerId): EitherT[F, String, Option[CustomerRecord]]
}

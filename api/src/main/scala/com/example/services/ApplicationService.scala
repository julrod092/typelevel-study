package com.example.services

import pricing.OrderPriceDTO
import pricing.PriceAPIGen.OrderPricingError
import pricing.OrderPricedDTO
import io.scalaland.chimney.dsl.*
import com.example.infrastructure.transformers.ApiTransformer.*
import com.example.domain.models.CustomerOrder
import cats.data.EitherT
import com.example.domain.services.ValidationService
import cats.instances.all._
import cats.syntax.all._
import com.example.domain.services.OrderPriceService
import cats.effect.Async
import com.example.domain.models.PrincingError

object ApplicationService {

  def createOrder[F[_]: Async](dto: OrderPriceDTO): EitherT[F, OrderPricingError, OrderPricedDTO] = {
    val toDomain: CustomerOrder = dto.transformInto[CustomerOrder]
    for {
      validatedCustomerOrder <- EitherT.pure[F, PrincingError](ValidationService.validate(toDomain).toEither)
      orderCreation <- EitherT.pure[F, PrincingError](OrderPriceService.createOrder(validatedCustomerOrder, None))
    } yield null
  }
}

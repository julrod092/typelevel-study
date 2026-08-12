package com.example.infrastructure.routes

import cats.data.EitherT
import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import cats.syntax.semigroupk.*
import com.example.infrastructure.configuration.PricingEnvironment
import org.http4s.HttpRoutes
import pricing.PriceAPI
import pricing.PriceAPIOperation.OrderPricingError
import smithy4s.http4s.SimpleRestJsonBuilder

trait Routes {

  type Result[F[_], T] = EitherT[F, OrderPricingError, T]

  def handleResult[F[_]: Async, T](result: Result[F, T]): F[T] =
    result.value.flatMap {
      case Right(res) => Async[F].pure(res)
      case Left(error) => Async[F].raiseError(OrderPricingError.unliftError(error))
    }
}

object Routes {

  private def docsRoute[F[_]: Sync]: HttpRoutes[F] =
    smithy4s.http4s.swagger.docs[F](PriceAPI)

  def impl[F[_]: Async](env: PricingEnvironment[F]): Resource[F, HttpRoutes[F]] =
    for {
      pricingRoutes <- SimpleRestJsonBuilder.routes(PriceRoutes.impl[F](env)).resource
    } yield pricingRoutes <+> docsRoute[F]
}

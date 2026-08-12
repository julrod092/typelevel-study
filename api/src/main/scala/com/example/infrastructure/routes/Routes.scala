package com.example.infrastructure.routes

import cats.data.EitherT
import cats.effect.{Async, Resource, Sync}
import cats.syntax.semigroupk.*
import cats.syntax.all.*
import com.example.infrastructure.routes.{HelloWorldRoutes, PriceRoutes}
import hello.HelloWorldAPI
import org.http4s.HttpRoutes
import pricing.PriceAPI
import pricing.PriceAPIOperation.GetPriceError
import smithy4s.http4s.SimpleRestJsonBuilder

trait Routes {

  type Result[F[_], T] = EitherT[F, GetPriceError, T]

  def handleResult[F[_] : Async, T](result: Result[F, T]): F[T] =
    result.value.flatMap {
      case Right(res) => Async[F].pure(res)
      case Left(error) => Async[F].raiseError {
        GetPriceError.unliftError(error)
      }
    }
}

object Routes {

  private def docsRoute[F[_]: Sync]: HttpRoutes[F] =
    smithy4s.http4s.swagger.docs[F](HelloWorldAPI, PriceAPI)

  def impl[F[_]: Async]: Resource[F, HttpRoutes[F]] =
    for {
      helloRoutes <- SimpleRestJsonBuilder.routes(HelloWorldRoutes.impl[F]).resource
      pricingRoutes <- SimpleRestJsonBuilder.routes(PriceRoutes.impl[F]).resource
    } yield helloRoutes <+> pricingRoutes <+> docsRoute[F]
}

package com.example.infrastructure.routes

import cats.effect.{Async, Resource, Sync}
import cats.syntax.semigroupk.*
import com.example.infrastructure.routes.{HelloWorldRoutes, PriceRoutes}
import hello.HelloWorldAPI
import org.http4s.HttpRoutes
import pricing.PriceAPI
import smithy4s.http4s.SimpleRestJsonBuilder

object Routes {

  private def docsRoute[F[_]: Sync]: HttpRoutes[F] =
    smithy4s.http4s.swagger.docs[F](HelloWorldAPI, PriceAPI)

  def impl[F[_]: Async]: Resource[F, HttpRoutes[F]] =
    for {
      helloRoutes <- SimpleRestJsonBuilder.routes(HelloWorldRoutes.impl[F]).resource
      pricingRoutes <- SimpleRestJsonBuilder.routes(PriceRoutes.impl[F]).resource
    } yield helloRoutes <+> pricingRoutes <+> docsRoute[F]
}

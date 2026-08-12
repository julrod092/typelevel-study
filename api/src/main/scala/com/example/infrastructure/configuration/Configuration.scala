package com.example.infrastructure.configuration

import cats.effect.Async
import com.comcast.ip4s.*
import com.example.domain.repositories.{CouponsRepository, CustomersRepository, OrdersRepository}
import com.example.infrastructure.routes.Routes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger

final case class PricingEnvironment[F[_]](
    customers: CustomersRepository[F],
    coupons: CouponsRepository[F],
    orders: OrdersRepository[F]
)

object Configuration {

  def run[F[_]: Async]: F[Nothing] = {
    for {
      routes <- Routes.impl[F]
      finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(routes.orNotFound)
      _ <- EmberServerBuilder
        .default[F]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(finalHttpApp)
        .build
    } yield ()
  }.useForever
}

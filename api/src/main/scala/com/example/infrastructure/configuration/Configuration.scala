package com.example.infrastructure.configuration

import cats.effect.{Async, Resource}
import com.amazonaws.dynamodb.DynamoDB
import com.comcast.ip4s.*
import com.example.domain.repositories.{CouponsRepository, CustomersRepository, OrdersRepository}
import com.example.infrastructure.database.{DynamoCouponRepository, DynamoCustomerRepository, DynamoOrderRepository}
import com.example.infrastructure.routes.Routes
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger
import smithy4s.aws.{AwsClient, AwsEnvironment, AwsRegion}

final case class PricingEnvironment[F[_]](
    customers: CustomersRepository[F],
    coupons: CouponsRepository[F],
    orders: OrdersRepository[F]
)

object Configuration {

  def createEnv[F[_]: Async]: Resource[F, PricingEnvironment[F]] =
    for {
      client <- EmberClientBuilder.default[F].withoutCheckEndpointAuthentication.build
      dynamoClient <- AwsClient(DynamoDB.service, AwsEnvironment.make[F](null, null, null, null))
    } yield PricingEnvironment(
      customers = DynamoCustomerRepository[F](dynamoClient),
      coupons = DynamoCouponRepository[F](dynamoClient),
      orders = DynamoOrderRepository[F](dynamoClient)
    )

  def run[F[_]: Async]: F[Nothing] = {
    for {
      env <- createEnv[F]
      routes <- Routes.impl[F](env)
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

package com.example.infrastructure.configuration

import cats.effect.{Async, Resource}
import com.amazonaws.dynamodb.DynamoDB
import com.example.domain.repositories.{CouponsRepository, CustomersRepository, OrdersRepository}
import com.example.infrastructure.database.{
  DynamoCouponRepository,
  DynamoCustomerRepository,
  DynamoOrderRepository
}
import com.example.infrastructure.routes.Routes
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger
import smithy4s.aws.{AwsClient, AwsEnvironment}

final case class PricingEnvironment[F[_]](
    customers: CustomersRepository[F],
    coupons: CouponsRepository[F],
    orders: OrdersRepository[F]
)

object Configuration {

  private def environment[F[_]: Async](config: AppConfig): Resource[F, PricingEnvironment[F]] =
    for {
      client <- EmberClientBuilder.default[F].withoutCheckEndpointAuthentication.build
      dynamoClient <- AwsClient(DynamoDB.service, AwsEnvironment.make[F](null, null, null, null))
      config <- AppConfig.config.resource[F]
    } yield PricingEnvironment(
      customers = DynamoCustomerRepository[F](dynamoClient, config.tables.customersTableName),
      coupons = DynamoCouponRepository[F](dynamoClient, config.tables.couponsTableName),
      orders = DynamoOrderRepository[F](dynamoClient, config.tables.ordersTableName)
    )

  def service[F[_]: Async]: Resource[F, Unit] = {
    for {
      config <- AppConfig.config.resource[F]
      env <- environment[F](config)
      routes <- Routes.impl[F](env)
      finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(routes.orNotFound)
      _ <- EmberServerBuilder
        .default[F]
        .withHost(config.http.host)
        .withPort(config.http.port)
        .withHttpApp(finalHttpApp)
        .build
    } yield ()
  }
}

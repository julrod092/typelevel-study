package com.example.infrastructure.configuration

import cats.effect.std.UUIDGen
import cats.effect.{Async, Clock, Resource}
import cats.implicits.*
import com.amazonaws.dynamodb.DynamoDB
import com.example.domain.repositories.{CouponsRepository, CustomersRepository, OrdersRepository}
import com.example.infrastructure.database.{
  DynamoCouponRepository,
  DynamoCustomerRepository,
  DynamoOrderRepository
}
import com.example.infrastructure.routes.Routes
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.http4s.server.middleware.Logger
import org.http4s.{HttpApp, Uri}
import smithy4s.aws.{AwsClient, AwsEnvironment, Timestamp}
import smithy4s.{Endpoint, Hints}

final case class PricingEnvironment[F[_]](
    customers: CustomersRepository[F],
    coupons: CouponsRepository[F],
    orders: OrdersRepository[F],
    clock: Clock[F],
    idGenerator: UUIDGen[F]
)

object Configuration {

  private def localStackMiddleware[F[_]: Async](
      baseUri: Uri
  ): Endpoint.Middleware[Client[F]] =
    new Endpoint.Middleware.Simple[Client[F]] {

      override def prepareWithHints(
          serviceHints: Hints,
          endpointHints: Hints
      ): Client[F] => Client[F] =
        underlying =>
          Client { request =>
            val target = baseUri.copy(
              path = baseUri.path.concat(request.uri.path),
              query = request.uri.query,
              fragment = None
            )

            underlying.run(request.withUri(target))
          }
    }

  def dynamoClient[F[_]: Async](config: AppConfig.AwsConfig): Resource[F, DynamoDB[F]] =
    for {
      client <- EmberClientBuilder.default[F].withoutCheckEndpointAuthentication.build
      awsEnvironment = AwsEnvironment
        .make[F](
          client = client,
          awsRegion = Async[F].pure(config.region),
          creds = Async[F].pure(config.token),
          time = Async[F].realTime.map(duration => Timestamp.fromEpochMilli(duration.toMillis))
        )
        .withMiddleware(localStackMiddleware[F](config.url))
      dynamoClient <- AwsClient(DynamoDB.service, awsEnvironment)
    } yield dynamoClient

  def environment[F[_]: Async](config: AppConfig): Resource[F, PricingEnvironment[F]] =
    for {
      client <- dynamoClient[F](config.aws)
    } yield PricingEnvironment(
      customers = DynamoCustomerRepository[F](client, config.tables.customersTableName),
      coupons = DynamoCouponRepository[F](client, config.tables.couponsTableName),
      orders = DynamoOrderRepository[F](client, config.tables.ordersTableName),
      clock = Clock[F],
      idGenerator = UUIDGen[F]
    )

  def httpApp[F[_]: Async](environment: PricingEnvironment[F]): Resource[F, HttpApp[F]] =
    Routes
      .impl[F](environment)
      .map(routes => Logger.httpApp(logHeaders = true, logBody = true)(routes.orNotFound))

  def server[F[_]: Async](
      config: AppConfig.HttpConfig,
      app: HttpApp[F]
  ): Resource[F, Server] =
    EmberServerBuilder
      .default[F]
      .withHost(config.host)
      .withPort(config.port)
      .withHttpApp(app)
      .build

  def service[F[_]: Async]: Resource[F, Unit] = {
    for {
      config <- AppConfig.config.resource[F]
      env <- environment[F](config)
      app <- httpApp[F](env)
      _ <- server[F](config.http, app)
    } yield ()
  }
}

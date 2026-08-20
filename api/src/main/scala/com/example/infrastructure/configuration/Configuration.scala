package com.example.infrastructure.configuration

import cats.effect.{Async, Resource}
import cats.implicits.*
import cats.syntax.all.*
import com.amazonaws.dynamodb.DynamoDB
import com.example.domain.repositories.{CouponsRepository, CustomersRepository, OrdersRepository}
import com.example.infrastructure.database.{
  DynamoCouponRepository,
  DynamoCustomerRepository,
  DynamoOrderRepository
}
import com.example.infrastructure.routes.Routes
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger
import smithy4s.{Endpoint, Hints}
import smithy4s.aws.{AwsClient, AwsEnvironment, Timestamp}
import com.comcast.ip4s.ipv4
import com.comcast.ip4s.port


final case class PricingEnvironment[F[_]](
    customers: CustomersRepository[F],
    coupons: CouponsRepository[F],
    orders: OrdersRepository[F]
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
      awsUrl <- Resource.eval[F, Uri](
        config.url
          .fold[F[Uri]](Async[F].raiseError(new IllegalArgumentException("Invalid Aws Uri")))(uri =>
            Async[F].pure(uri)
          )
      )
      awsEnvironment = AwsEnvironment
        .make[F](
          client = client,
          awsRegion = Async[F].pure(config.region),
          creds = Async[F].pure(config.token),
          time = Async[F].realTime.map(duration => Timestamp.fromEpochMilli(duration.toMillis))
        )
        .withMiddleware(localStackMiddleware[F](awsUrl))
      dynamoClient <- AwsClient(DynamoDB.service, awsEnvironment)
    } yield dynamoClient

  def environment[F[_]: Async](config: AppConfig): Resource[F, PricingEnvironment[F]] =
    for {
      client <- dynamoClient[F](config.aws)
    } yield PricingEnvironment(
      customers = DynamoCustomerRepository[F](client, config.tables.customersTableName),
      coupons = DynamoCouponRepository[F](client, config.tables.couponsTableName),
      orders = DynamoOrderRepository[F](client, config.tables.ordersTableName)
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

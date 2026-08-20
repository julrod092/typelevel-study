package com.example.support

import cats.effect.{IO, Resource}
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import com.amazonaws.dynamodb.{DynamoDB, TableArn}
import com.example.infrastructure.configuration.{AppConfig, Configuration}
import com.example.infrastructure.database.{DynamoCouponRepository, DynamoCustomerRepository, DynamoOrderRepository}
import com.example.infrastructure.database.DynamoSchema
import org.http4s.Uri
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import smithy4s.aws.{AwsCredentials, AwsRegion}

final case class DynamoFixture(
    client: DynamoDB[IO],
    customers: DynamoCustomerRepository[IO],
    coupons: DynamoCouponRepository[IO],
    orders: DynamoOrderRepository[IO],
    tables: AppConfig.DynamoTables
)

object LocalStackDynamoFixture {
  private val image = DockerImageName.parse("localstack/localstack:4.0.3")

  val resource: Resource[IO, DynamoFixture] = for {
    container <- Resource
      .fromAutoCloseable(
        IO.blocking {
          val localStack = new LocalStackContainer(image).withServices("dynamodb")
          sys.env.get("LOCALSTACK_AUTH_TOKEN").fold(localStack)(token =>
            localStack.withEnv("LOCALSTACK_AUTH_TOKEN", token)
          )
        }
      )
      .evalTap(container => IO.blocking(container.start()))
    endpoint <- Resource.eval(IO.fromEither(Uri.fromString(container.getEndpoint.toString)))
    awsConfig = AppConfig.AwsConfig(
      region = AwsRegion(container.getRegion),
      url = Some(endpoint),
      token = AwsCredentials.Default(
        accessKeyId = container.getAccessKey,
        secretAccessKey = container.getSecretKey,
        sessionToken = None
      )
    )
    client <- Configuration.dynamoClient[IO](awsConfig)
    tables <- Resource.eval(uniqueTables)
    _ <- Resource.eval(
      List(
        tables.customersTableName -> DynamoSchema.CustomerId.value,
        tables.couponsTableName -> DynamoSchema.CouponCode.value,
        tables.ordersTableName -> DynamoSchema.OrderId.value
      ).traverse_ { case (table, key) => DynamoTestSupport.createTable(client, table, key) }
    )
  } yield DynamoFixture(
    client = client,
    customers = DynamoCustomerRepository[IO](client, tables.customersTableName),
    coupons = DynamoCouponRepository[IO](client, tables.couponsTableName),
    orders = DynamoOrderRepository[IO](client, tables.ordersTableName),
    tables = tables
  )

  private def uniqueTables: IO[AppConfig.DynamoTables] =
    List.fill(3)(UUIDGen[IO].randomUUID).sequence.map { ids =>
      AppConfig.DynamoTables(
        customersTableName = TableArn(s"it-customers-${ids(0)}"),
        couponsTableName = TableArn(s"it-coupons-${ids(1)}"),
        ordersTableName = TableArn(s"it-orders-${ids(2)}")
      )
    }
}

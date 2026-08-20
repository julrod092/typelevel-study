package com.example.infrastructure.configuration

import cats.effect.IO
import cats.syntax.all.*
import ciris.{ConfigError, ConfigKey, ConfigValue, Effect}
import smithy4s.aws.AwsCredentials
import weaver.SimpleIOSuite

object AppConfigSuite extends SimpleIOSuite {

  private val validEnvironment = Map(
    "SERVICE_PORT" -> "8080",
    "SERVICE_HOST" -> "127.0.0.1",
    "ORDERS_TABLE_NAME" -> "typelevel-orders",
    "CUSTOMERS_TABLE_NAME" -> "typelevel-customers",
    "COUPONS_TABLE_NAME" -> "typelevel-coupons",
    "AWS_DEFAULT_REGION" -> "us-east-1",
    "AWS_ENDPOINT_URL" -> "http://localhost:4566",
    "AWS_ACCESS_KEY_ID" -> "local-access-key",
    "AWS_SECRET_ACCESS_KEY" -> "local-secret-key"
  )

  test(
    "A service process with every required environment value loads the complete HTTP, DynamoDB, endpoint, and credential configuration"
  ) {
    val environment = validEnvironment.updated("AWS_SESSION_TOKEN", "local-session-token")

    load(environment).map {
      case Right(config) =>
        expect(config.http.host.toString == "127.0.0.1") and
          expect(config.http.port.value == 8080) and
          expect(config.tables.ordersTableName.value == "typelevel-orders") and
          expect(config.tables.customersTableName.value == "typelevel-customers") and
          expect(config.tables.couponsTableName.value == "typelevel-coupons") and
          expect(config.aws.region.value == "us-east-1") and
          expect(config.aws.url.renderString == "http://localhost:4566") and
          expect(
            config.aws.token == AwsCredentials.Default(
              accessKeyId = "local-access-key",
              secretAccessKey = "local-secret-key",
              sessionToken = Some("local-session-token")
            )
          )
      case Left(error) => failure(error.messages.toList.mkString(", "))
    }
  }

  test("A service process may omit AWS_SESSION_TOKEN without weakening required configuration") {
    load(validEnvironment).map {
      case Right(config) =>
        expect(
          config.aws.token == AwsCredentials.Default(
            accessKeyId = "local-access-key",
            secretAccessKey = "local-secret-key",
            sessionToken = None
          )
        )
      case Left(error) => failure(error.messages.toList.mkString(", "))
    }
  }

  test(
    "When several required environment values are absent, configuration reports every missing key before creating infrastructure"
  ) {
    val incomplete = validEnvironment -- Set(
      "SERVICE_PORT",
      "CUSTOMERS_TABLE_NAME",
      "AWS_ENDPOINT_URL",
      "AWS_SECRET_ACCESS_KEY"
    )

    load(incomplete).map {
      case Left(error) =>
        val messages = error.messages.toList.mkString(" ")

        expect(messages.contains("SERVICE_PORT")) and
          expect(messages.contains("CUSTOMERS_TABLE_NAME")) and
          expect(messages.contains("AWS_ENDPOINT_URL")) and
          expect(messages.contains("AWS_SECRET_ACCESS_KEY"))
      case Right(config) => failure(s"Expected configuration failure, received $config")
    }
  }

  test(
    "Invalid host, port, and endpoint values are accumulated while credential values remain absent from diagnostics"
  ) {
    val invalid = validEnvironment
      .updated("SERVICE_HOST", "not a valid host")
      .updated("SERVICE_PORT", "70000")
      .updated("AWS_ENDPOINT_URL", "http://[invalid")

    load(invalid).map {
      case Left(error) =>
        val messages = error.messages.toList.mkString(" ")

        expect(messages.contains("SERVICE_HOST")) and
          expect(messages.contains("SERVICE_PORT")) and
          expect(messages.contains("AWS_ENDPOINT_URL")) and
          expect(!messages.contains("local-access-key")) and
          expect(!messages.contains("local-secret-key"))
      case Right(config) => failure(s"Expected configuration failure, received $config")
    }
  }

  test("A LocalStack endpoint must be an absolute HTTP or HTTPS URI with an authority") {
    List("", "/localstack", "ftp://localhost:4566", "http://", "http://:4566")
      .traverse { endpoint =>
        load(validEnvironment.updated("AWS_ENDPOINT_URL", endpoint))
      }
      .map { results =>
        expect(
          results.forall(
            _.left.toOption.exists(_.messages.toList.exists(_.contains("AWS_ENDPOINT_URL")))
          )
        )
      }
  }

  private def load(values: Map[String, String]): IO[Either[ConfigError, AppConfig]] =
    AppConfig.configFrom(source(values)).attempt[IO]

  private def source(values: Map[String, String]): AppConfig.ConfigSource =
    name =>
      values.get(name) match {
        case Some(value) => ConfigValue.loaded(ConfigKey.env(name), value)
        case None        => ConfigValue.missing(ConfigKey.env(name))
      }
}

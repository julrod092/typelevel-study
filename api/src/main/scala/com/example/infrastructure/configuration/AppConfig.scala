package com.example.infrastructure.configuration

import cats.effect.Async
import cats.syntax.all.*
import ciris.{ConfigValue, env}
import ciris.http4s.*
import com.amazonaws.dynamodb.TableArn
import com.comcast.ip4s.{Host, Port, ipv4, port}
import org.http4s.Uri
import smithy4s.aws.{AwsCredentials, AwsRegion}

final case class AppConfig(
    aws: AppConfig.AwsConfig,
    tables: AppConfig.DynamoTables,
    http: AppConfig.HttpConfig
)

object AppConfig {

  final case class DynamoTables(
      couponsTableName: TableArn,
      customersTableName: TableArn,
      ordersTableName: TableArn
  )

  final case class AwsConfig(
      region: AwsRegion,
      url: Option[Uri],
      token: AwsCredentials
  )

  final case class HttpConfig(
      host: Host,
      port: Port
  )

  def config[F[_]: Async]: ConfigValue[F, AppConfig] =
    (
      env("SERVICE_PORT").as[Port],
      env("SERVICE_HOST").as[Host],
      env("ORDERS_TABLE_NAME").as[String],
      env("CUSTOMERS_TABLE_NAME").as[String],
      env("COUPONS_TABLE_NAME").as[String],
      env("AWS_REGION").as[String],
      env("AWS_URL").as[String].option,
      env("AWS_ACCESS_KEY").as[String].secret,
      env("AWS_API_KEY").as[String].secret,
      env("AWS_SESSION_TOKEN").as[String].option.secret
    ).parMapN {
      (
          port,
          host,
          ordersTableName,
          customersTableName,
          couponsTableName,
          awsRegion,
          awsUrl,
          awsAccessKey,
          awsApiKey,
          awsSessionToken
      ) =>
        AppConfig(
          aws = AwsConfig(
            region = AwsRegion(awsRegion),
            url = awsUrl.flatMap(Uri.fromString(_).toOption),
            token = AwsCredentials
              .Default(
                accessKeyId = awsAccessKey.value,
                secretAccessKey = awsApiKey.value,
                sessionToken = awsSessionToken.value
              )
          ),
          tables = DynamoTables(
            couponsTableName = TableArn(couponsTableName),
            customersTableName = TableArn(customersTableName),
            ordersTableName = TableArn(ordersTableName)
          ),
          http = HttpConfig(host = host, port = port)
        )
    }
}

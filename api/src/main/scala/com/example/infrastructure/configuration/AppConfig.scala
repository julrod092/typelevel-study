package com.example.infrastructure.configuration

import cats.syntax.all.*
import ciris.{ConfigDecoder, ConfigValue, Effect, env}
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

  private val endpointDecoder: ConfigDecoder[String, Uri] =
    ConfigDecoder[String, Uri].collect("absolute HTTP(S) URI") {
      case uri
          if uri.host.exists(_.value.nonEmpty) && uri.scheme
            .exists(scheme => scheme == Uri.Scheme.http || scheme == Uri.Scheme.https) =>
        uri
    }

  final case class DynamoTables(
      couponsTableName: TableArn,
      customersTableName: TableArn,
      ordersTableName: TableArn
  )

  final case class AwsConfig(
      region: AwsRegion,
      url: Uri,
      token: AwsCredentials
  )

  final case class HttpConfig(
      host: Host,
      port: Port
  )

  private[configuration] type ConfigSource = String => ConfigValue[Effect, String]

  private[configuration] def configFrom(source: ConfigSource): ConfigValue[Effect, AppConfig] =
    (
      source("SERVICE_PORT").as[Port],
      source("SERVICE_HOST").as[Host],
      source("ORDERS_TABLE_NAME").as[String],
      source("CUSTOMERS_TABLE_NAME").as[String],
      source("COUPONS_TABLE_NAME").as[String],
      source("AWS_DEFAULT_REGION").as[String],
      source("AWS_ENDPOINT_URL").as[Uri](using endpointDecoder),
      source("AWS_ACCESS_KEY_ID").as[String].secret,
      source("AWS_SECRET_ACCESS_KEY").as[String].secret,
      source("AWS_SESSION_TOKEN").as[String].option.secret
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
            url = awsUrl,
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

  def config: ConfigValue[Effect, AppConfig] =
    configFrom(name => env(name))
}

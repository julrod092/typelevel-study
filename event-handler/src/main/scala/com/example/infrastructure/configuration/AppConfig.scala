package com.example.infrastructure.configuration

import cats.syntax.all.*
import ciris.{ConfigDecoder, ConfigValue, Effect, env}
import software.amazon.awssdk.regions.Region

import java.net.URI
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final case class AppConfig(
    region: Region,
    endpoint: Option[URI],
    streamName: String,
    parallelism: Int,
    streamChunkSize: Int,
    streamChunkTimeWindow: FiniteDuration
)

object AppConfig {

  private[configuration] type ConfigSource =
    String => ConfigValue[Effect, String]

  private val endpointDecoder: ConfigDecoder[String, URI] =
    ConfigDecoder[String].mapOption("absolute HTTP(S) URI") { value =>
      Either.catchOnly[IllegalArgumentException](URI.create(value)).toOption.filter { uri =>
        uri.isAbsolute &&
        Option(uri.getScheme)
          .exists(scheme => scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) &&
        Option(uri.getHost).exists(_.nonEmpty)
      }
    }

  private[configuration] def configFrom(
      source: ConfigSource
  ): ConfigValue[Effect, AppConfig] =
    (
      source("AWS_DEFAULT_REGION").as[String].map(Region.of),
      source("AWS_ENDPOINT_URL").as(using endpointDecoder).option,
      source("KINESIS_STREAM_NAME").as[String],
      source("EVENT_HANDLER_PARALLELISM").as[Int].default(4),
      source("STREAM_CHUNK_SIZE").as[Int].default(3),
      source("STREAM_CHUNK_TIME_WINDOW").as[FiniteDuration].default(1.second)
    ).parMapN(AppConfig.apply)

  val config: ConfigValue[Effect, AppConfig] =
    configFrom(name => env(name))
}

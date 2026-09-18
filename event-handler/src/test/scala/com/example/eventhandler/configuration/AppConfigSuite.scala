package com.example.eventhandler.configuration

import cats.effect.IO
import cats.syntax.all.*
import ciris.{ConfigError, ConfigKey, ConfigValue}
import software.amazon.awssdk.regions.Region
import weaver.SimpleIOSuite

import java.net.URI
import scala.concurrent.duration.*

object AppConfigSuite extends SimpleIOSuite {

  private val validEnvironment = Map(
    "AWS_DEFAULT_REGION" -> "us-east-1",
    "KINESIS_STREAM_NAME" -> "order-priced-events"
  )

  test("loads all defaults from the required environment") {
    val expected = AppConfig(Region.US_EAST_1, None, "order-priced-events", 4, 3, 1.second)

    load(validEnvironment).map { result =>
      expect(result == Right(expected))
    }
  }

  test("loads explicit region, endpoint, stream and batching overrides") {
    val endpoint = URI.create("http://localhost:4566")
    val environment = validEnvironment ++ Map(
      "AWS_DEFAULT_REGION" -> "eu-west-1",
      "AWS_ENDPOINT_URL" -> endpoint.toString,
      "KINESIS_STREAM_NAME" -> "custom-events",
      "EVENT_HANDLER_PARALLELISM" -> "8",
      "STREAM_CHUNK_SIZE" -> "25",
      "STREAM_CHUNK_TIME_WINDOW" -> "250 milliseconds"
    )
    val expected = AppConfig(Region.EU_WEST_1, Some(endpoint), "custom-events", 8, 25, 250.millis)

    load(environment).map { result =>
      expect(result == Right(expected))
    }
  }

  test("reports both missing required configuration keys") {
    load(Map.empty).map { result =>
      expect(errorMentions(result, "AWS_DEFAULT_REGION")) and
        expect(errorMentions(result, "KINESIS_STREAM_NAME"))
    }
  }

  test("reports malformed parallelism, chunk size and time window") {
    load(
      validEnvironment ++ Map(
        "EVENT_HANDLER_PARALLELISM" -> "many",
        "STREAM_CHUNK_SIZE" -> "several",
        "STREAM_CHUNK_TIME_WINDOW" -> "soon"
      )
    ).map { result =>
      expect(errorMentions(result, "EVENT_HANDLER_PARALLELISM")) and
        expect(errorMentions(result, "STREAM_CHUNK_SIZE")) and
        expect(errorMentions(result, "STREAM_CHUNK_TIME_WINDOW"))
    }
  }

  test("accepts absolute HTTP and HTTPS endpoints") {
    List("http://localhost:4566", "https://kinesis.example.com")
      .traverse { endpoint =>
        load(validEnvironment.updated("AWS_ENDPOINT_URL", endpoint)).map { result =>
          expect(result.exists(_.endpoint.contains(URI.create(endpoint))))
        }
      }
      .map(_.reduce(_ and _))
  }

  test("rejects empty, relative, unsupported and malformed endpoints") {
    List("", "/localstack", "ftp://localhost:4566", "http://", "http://:4566", "http://[invalid")
      .traverse { endpoint =>
        load(validEnvironment.updated("AWS_ENDPOINT_URL", endpoint)).map { result =>
          expect(errorMentions(result, "AWS_ENDPOINT_URL"))
        }
      }
      .map(_.reduce(_ and _))
  }

  test("rejects an infinite chunk time window") {
    load(validEnvironment.updated("STREAM_CHUNK_TIME_WINDOW", "Inf")).map { result =>
      expect(errorMentions(result, "STREAM_CHUNK_TIME_WINDOW"))
    }
  }

  test("uses AWS_DEFAULT_REGION even when AWS_REGION is also present") {
    val expectedRegion = Region.US_EAST_1
    load(validEnvironment.updated("AWS_REGION", "eu-west-1")).map { result =>
      expect(result.exists(_.region == expectedRegion))
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

  private def errorMentions(result: Either[ConfigError, AppConfig], key: String): Boolean =
    result.left.exists(_.messages.toList.exists(_.contains(key)))
}

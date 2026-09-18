package com.example.support

import cats.effect.{IO, Resource}
import weaver.IOSuite
import weaver.scalacheck.{CheckConfig, Checkers}

trait KinesisIntegrationSuite extends IOSuite with Checkers {
  override type Res = KinesisFixture

  override def sharedResource: Resource[IO, Res] = LocalStackKinesisFixture.resource

  override def checkConfig: CheckConfig =
    CheckConfig.default.copy(minimumSuccessful = 20, perPropertyParallelism = 1)
}

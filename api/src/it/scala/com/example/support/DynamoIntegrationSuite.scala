package com.example.support

import cats.effect.{IO, Resource}
import weaver.IOSuite
import weaver.scalacheck.{CheckConfig, Checkers}

trait DynamoIntegrationSuite extends IOSuite with Checkers {
  override type Res = DynamoFixture

  override def sharedResource: Resource[IO, Res] = LocalStackDynamoFixture.resource

  override def checkConfig: CheckConfig =
    CheckConfig.default.copy(minimumSuccessful = 20, perPropertyParallelism = 1)
}

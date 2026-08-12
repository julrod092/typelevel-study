package com.example.infrastructure.routes

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import weaver.SimpleIOSuite

final class HelloWorldRoutesSpec extends SimpleIOSuite {

  test("return a greeting without a town") {
    HelloWorldRoutes
      .impl[IO]
      .hello("Ada", None)
      .asserting(_ shouldBe Greeting("Hello Ada!"))
  }

  test("include the town when provided") {
    HelloWorldRoutes
      .impl[IO]
      .hello("Ada", Some("London"))
      .asserting(_ shouldBe Greeting("Hello Ada from London!"))
  }
}

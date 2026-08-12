package com.example.infrastructure

import cats.effect.IO
import com.example.infrastructure.routes.Routes
import org.http4s.*
import org.http4s.implicits.*
import weaver.SimpleIOSuite

object RoutesSpec extends SimpleIOSuite {

  private def responseFor(request: Request[IO]): IO[(Status, String)] =
    Routes.impl[IO].use { routes =>
      routes.orNotFound.run(request).flatMap { response =>
        response.as[String].map(body => response.status -> body)
      }
    }

  test("GET /hello/{name} returns a greeting") {
    responseFor(Request[IO](Method.GET, uri"/hello/Ada")).map { case (status, body) =>
      expect.all(
        status == Status.Ok,
        body == """{"message":"Hello Ada!"}""",
      )
    }
  }

  test("GET /hello/{name} accepts a town query parameter") {
    responseFor(Request[IO](Method.GET, uri"/hello/Ada?town=London")).map { case (status, body) =>
      expect.all(
        status == Status.Ok,
        body == """{"message":"Hello Ada from London!"}""",
      )
    }
  }

  test("an unknown route returns 404") {
    responseFor(Request[IO](Method.GET, uri"/unknown")).map { case (status, _) =>
      expect(status == Status.NotFound)
    }
  }
}

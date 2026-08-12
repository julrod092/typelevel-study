package com.example.infrastructure.routes

import cats.effect.{Async, Concurrent}
import cats.implicits.*
import hello.{GreetingDTO, HelloWorldAPI, HelloWorldAPIGen}
import smithy4s.kinds.FunctorAlgebra

object HelloWorldRoutes {

  def impl[F[_]: Async]: FunctorAlgebra[HelloWorldAPIGen, F] =
    (name: String, town: Option[String]) =>
      (town match {
        case None    => GreetingDTO(s"Hello " + name + "!")
        case Some(t) => GreetingDTO(s"Hello " + name + " from " + t + "!")
      }).pure[F]
}

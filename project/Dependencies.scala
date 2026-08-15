import sbt.*

object Versions {
  val cats = "2.13.0"
  val catsEffect = "3.7.0"
  val catsEffectTesting = "1.8.0"
  val chimney = "1.11.0"
  val circe = "0.14.16"
  val ciris = "3.15.0"
  val fs2 = "3.13.0"
  val http4s = "0.23.36"
  val natchez = "0.3.10"
  val scalaTest = "3.2.19"
  val smithy4s = "0.19.11"
  val weaver = "0.13.0"
}

object Dependencies {

  val common: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-core" % Versions.cats,
    "org.typelevel" %% "cats-effect" % Versions.catsEffect,
    "org.tpolecat" %% "natchez-core" % Versions.natchez,
    "io.scalaland" %% "chimney" % Versions.chimney,
    "is.cir" %% "ciris" % Versions.ciris,
    "is.cir" %% "ciris-refined" % Versions.ciris,
    "is.cir" %% "ciris-http4s" % Versions.ciris,
    "org.typelevel" %% "weaver-cats" % Versions.weaver % Test
  )

  val api: Seq[ModuleID] = common ++ Seq(
    "com.disneystreaming.smithy4s" %% "smithy4s-core" % Versions.smithy4s,
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % Versions.smithy4s,
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % Versions.smithy4s,
    "com.disneystreaming.smithy4s" %% "smithy4s-aws-http4s" % Versions.smithy4s,
    "org.http4s" %% "http4s-ember-server" % Versions.http4s,
    "org.http4s" %% "http4s-ember-client" % Versions.http4s,
    "org.http4s" %% "http4s-circe" % Versions.http4s,
    "org.http4s" %% "http4s-dsl" % Versions.http4s,
    "io.circe" %% "circe-core" % Versions.circe,
    "io.circe" %% "circe-generic" % Versions.circe
  )

  val eventHandler: Seq[ModuleID] = common ++ Seq(
    "co.fs2" %% "fs2-core" % Versions.fs2
  )
}

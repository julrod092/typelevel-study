import sbt._
import sbt.Keys.test
import sbtassembly.AssemblyKeys._
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.{MergeStrategy, PathList}

object Versions {
  val awsSdk = "2.55.0"
  val cats = "2.13.0"
  val catsEffect = "3.7.1"
  val chimney = "1.11.0"
  val circe = "0.14.16"
  val ciris = "3.15.1"
  val fs2 = "3.14.0"
  val http4s = "0.23.37"
  val log4cats = "2.8.0"
  val monocle = "3.3.0"
  val natchez = "0.3.10"
  val scalaCheck = "1.20.0"
  val smithy4s = "0.19.12"
  val slf4j = "2.0.19"
  val testcontainers = "2.0.5"
  val weaver = "0.8.4"
  val awsLambda = "1.4.0"
  val awsLambdaEvents = "3.16.1"
}

object Dependencies {

  val common: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-core" % Versions.cats,
    "org.typelevel" %% "cats-effect" % Versions.catsEffect,
    "io.circe" %% "circe-core" % Versions.circe,
    "io.circe" %% "circe-generic" % Versions.circe,
    "org.tpolecat" %% "natchez-core" % Versions.natchez,
    "is.cir" %% "ciris" % Versions.ciris,
    "is.cir" %% "ciris-refined" % Versions.ciris,
    "is.cir" %% "ciris-http4s" % Versions.ciris,
    "org.tpolecat" %% "natchez-log" % Versions.natchez,
    "org.typelevel" %% "log4cats-slf4j" % Versions.log4cats,
    "org.slf4j" % "slf4j-simple" % Versions.slf4j,
    "com.disneystreaming" %% "weaver-cats" % Versions.weaver % Test,
    "com.disneystreaming" %% "weaver-scalacheck" % Versions.weaver % Test,
    "dev.optics" %% "monocle-core" % Versions.monocle % Test,
    "dev.optics" %% "monocle-macro" % Versions.monocle % Test,
    "org.scalacheck" %% "scalacheck" % Versions.scalaCheck % Test
  )

  val api: Seq[ModuleID] = Seq(
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % Versions.smithy4s,
    "com.disneystreaming.smithy4s" %% "smithy4s-core" % Versions.smithy4s,
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % Versions.smithy4s,
    "com.disneystreaming.smithy4s" %% "smithy4s-aws-http4s" % Versions.smithy4s,
    "io.scalaland" %% "chimney" % Versions.chimney,
    "org.http4s" %% "http4s-ember-server" % Versions.http4s,
    "org.http4s" %% "http4s-ember-client" % Versions.http4s,
    "org.http4s" %% "http4s-circe" % Versions.http4s,
    "org.http4s" %% "http4s-dsl" % Versions.http4s,
    "io.circe" %% "circe-core" % Versions.circe,
    "io.circe" %% "circe-generic" % Versions.circe
  )

  val eventHandler: Seq[ModuleID] = Seq(
    "co.fs2" %% "fs2-core" % Versions.fs2,
    "com.amazonaws" % "aws-lambda-java-core" % Versions.awsLambda,
    "com.amazonaws" % "aws-lambda-java-events" % Versions.awsLambdaEvents,
    "software.amazon.awssdk" % "kinesis" % Versions.awsSdk,
    "software.amazon.awssdk" % "netty-nio-client" % Versions.awsSdk,
    "io.circe" %% "circe-parser" % Versions.circe % Test,
    "org.typelevel" %% "cats-effect-testkit" % Versions.catsEffect % Test
  )

  val it: Seq[ModuleID] = Seq(
    "org.testcontainers" % "testcontainers-localstack" % Versions.testcontainers % Test,
    "org.testcontainers" % "testcontainers-localstack" % Versions.testcontainers % Test,
    "io.circe" %% "circe-parser" % Versions.circe % Test
  )
}

object Builder {

  def lambdaBuilder(
      name: String
  ): Seq[Def.Setting[_]] = Seq(
    assembly / assemblyJarName := name,
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _ @ _*)             => MergeStrategy.concat
      case PathList("META-INF", "smithy", _ @ _*)               => MergeStrategy.concat
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.concat
      case "reference.conf" | "application.conf"                => MergeStrategy.concat
      case path if path == "module-info.class" || path.endsWith("/module-info.class") =>
        MergeStrategy.discard
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", "INDEX.LIST")  => MergeStrategy.discard
      case PathList("META-INF", xs @ _*)
          if xs.lastOption.exists(name =>
            name.toLowerCase.endsWith(".sf") || name.toLowerCase
              .endsWith(".rsa") || name.toLowerCase.endsWith(".dsa") || name.toLowerCase
              .endsWith(".ec") || name.toLowerCase.startsWith("sig-")
          ) =>
        MergeStrategy.discard
      case PathList("META-INF", metadata, _ @ _*)
          if metadata.equalsIgnoreCase("dependencies") || metadata.toLowerCase
            .startsWith("license") || metadata.toLowerCase.startsWith("notice") =>
        MergeStrategy.discard
      case _ => MergeStrategy.deduplicate
    },
    assembly / test := {}
  )
}


lazy val commonSettings = Seq(
  name := "typelevel-project",
  scalaVersion := "3.8.4",
  organization := "com.example",
  libraryDependencies ++= Dependencies.common,
)

lazy val core = (project in file("core"))
  .settings(commonSettings)

lazy val api = (project in file("api"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(commonSettings)
  .settings(
    name += "-api",
    libraryDependencies ++= Dependencies.api,
    Compile / run / connectInput := true,
    smithy4sAwsSpecEntries ++= Seq(AWS.dynamodb)
  )
  .dependsOn(core)

lazy val eventHandler = (project in file("event-handler"))
  .settings(commonSettings)
  .settings(
    name += "-event-handler",
    libraryDependencies ++= Dependencies.eventHandler
  )
  .dependsOn(core)

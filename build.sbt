
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
  .dependsOn(core % "compile->compile;test->test")

lazy val apiIntegration = (project in file("api-integration"))
  .settings(commonSettings)
  .settings(
    name += "-api-integration",
    libraryDependencies ++= Dependencies.apiIntegration,
    Test / scalaSource := (api / baseDirectory).value / "src" / "it" / "scala",
    Test / resourceDirectory := (api / baseDirectory).value / "src" / "it" / "resources",
    Test / fork := true,
    Test / parallelExecution := false,
    publish / skip := true
  )
  .dependsOn(api % "compile->compile;test->test")

lazy val taskLambda = taskKey[File]("Unpack lambda")

lazy val eventHandler = (project in file("event-handler"))
  .enablePlugins(AssemblyPlugin)
  .settings(commonSettings)
  .settings(
    name += "-event-handler",
    Builder.lambdaBuilder("stream-processor.jar"),
    libraryDependencies ++= Dependencies.eventHandler,
    taskLambda := {
      val output = target.value / "lambda-hot"
      IO.delete(output)
      IO.createDirectory(output)
      IO.unzip((Compile / assembly).value, output)
      output
    }
  )
  .dependsOn(core)

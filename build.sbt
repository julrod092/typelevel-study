import sbt.internal.IvyConsole

lazy val commonSettings = Seq(
  name := "typelevel-project",
  scalaVersion := "3.8.4",
  organization := "com.example",
  libraryDependencies ++= Dependencies.common,
  testFrameworks += new TestFramework("weaver.framework.CatsEffect")
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

lazy val it = (project in file("it"))
  .settings(commonSettings)
  .settings(
    name += "-it",
    libraryDependencies ++= Dependencies.it,
    Test / unmanagedSourceDirectories := Seq(
      (api / baseDirectory).value / "src" / "it" / "scala",
      (eventHandler / baseDirectory).value / "src" / "it" / "scala"
    ),
    Test / unmanagedResourceDirectories := Seq(
      (api / baseDirectory).value / "src" / "it" / "resources",
      (eventHandler / baseDirectory).value / "src" / "it" / "resources"
    ),
    Test / fork := true,
    Test / parallelExecution := false,
    publish / skip := true
  )
  .dependsOn(api, eventHandler)

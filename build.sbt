
lazy val root = (project in file("."))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "typelevel-project",
    scalaVersion := "3.8.4",
    organization := "com.example",
    libraryDependencies ++= Dependencies.core,
    Compile / run / fork := true,
    Compile / run / connectInput := true,
    smithy4sAwsSpecEntries ++= Seq(AWS.dynamodb)
  )

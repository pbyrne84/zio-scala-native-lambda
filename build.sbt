name := "zio-scala-native-lambda"

scalaVersion := "2.13.10"

val zioVersion = "2.0.7"
val zioLoggingVersion = "2.1.8"
val circeVersion = "0.14.3"

ThisBuild / assemblyMergeStrategy := {
  case "application.conf" => MergeStrategy.concat
  case PathList("module-info.class") => MergeStrategy.discard
  case PathList("META-INF", "versions", xs @ _, "module-info.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

libraryDependencies ++= List(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-logging-slf4j" % zioLoggingVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "dev.zio" %% "zio-config-typesafe" % "3.0.7",
  "ch.qos.logback" % "logback-classic" % "1.4.5",
  "net.logstash.logback" % "logstash-logback-encoder" % "7.2",
  "com.softwaremill.sttp.client3" %% "zio" % "3.8.11", // for ZIO 2.x
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "com.github.tomakehurst" % "wiremock" % "2.27.2" % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test
)

lazy val jarName = "zio-scala-native-lambda.jar"

lazy val myNativeImageProject = (project in file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    Compile / mainClass := Some("com.github.pbyrne84.zioscalanativelambda.LambdaMain"),
    assembly / assemblyJarName := jarName
  )

// Forking will allow the agent to run
fork := true
Test / javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image"

// remember   "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

Test / test := (Test / test)
  .dependsOn(Compile / scalafmtCheck)
  .dependsOn(Test / scalafmtCheck)
  .value

//not to be used in ci, intellij has got a bit bumpy in the format on save
val formatAndTest =
  taskKey[Unit]("format all code then run tests, do not use on CI as any changes will not be committed")

formatAndTest := {
  (Test / test)
    .dependsOn(Compile / scalafmtAll)
    .dependsOn(Test / scalafmtAll)
}.value

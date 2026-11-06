val scala3Version   = "3.3.4"
val zioVersion      = "2.1.14"
val zioHttpVersion  = "3.0.1"
val zioJsonVersion  = "0.7.3"

lazy val root = project
  .in(file("."))
  .settings(
    name         := "order-flow-zio",
    version      := "0.1.0",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % zioVersion,
      "dev.zio" %% "zio-http"     % zioHttpVersion,
      "dev.zio" %% "zio-json"     % zioJsonVersion,
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
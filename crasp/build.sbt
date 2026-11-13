val scala3Version = "3.7.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "sls-automata-learning",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "org.typelevel" %% "cats-parse" % "0.3.10",

  )

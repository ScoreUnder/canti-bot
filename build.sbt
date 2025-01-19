import Dependencies._

enablePlugins(GitVersioning, ReproducibleBuildsPlugin)

ThisBuild / organization := "score.discord"
ThisBuild / scalaVersion := "3.3.1"
ThisBuild / git.useGitDescribe := true
ThisBuild / scalacOptions ++= List("-deprecation", "-unchecked", "-feature")
ThisBuild / resolvers ++= Seq(
  "jcenter-bintray" at "https://jcenter.bintray.com",
  "m2-dv8tion" at "https://m2.dv8tion.net/releases",
)
ThisBuild / run / fork := true

lazy val common = project in file("common-code")

lazy val database = (project in file("database-code"))
  .dependsOn(common)
  .settings(
    scalaVersion := "2.13.11",
    scalacOptions += "-Ytasty-reader",
    libraryDependencies ++= Seq(jda, "com.typesafe.slick" %% "slick" % "3.3.3"),
  )

lazy val tastyFile = "\\.tasty$".r.unanchored

lazy val root = (project in file("."))
  .dependsOn(common, database)
  .settings(
    name := "canti-bot",
    scalacOptions ++= Seq("-Yexplicit-nulls", "-Ysafe-init", "-source:future", "-explain"),
    libraryDependencies ++= Seq(
      scalaTest % Test,
      jda,
      "org.xerial" % "sqlite-jdbc" % "3.43.2.1",
      "com.typesafe" % "config" % "1.4.3",
      "com.github.rssh" %% "dotty-cps-async" % "0.9.19",
      "com.google.re2j" % "re2j" % "1.8",
      "org.slf4j" % "slf4j-simple" % "2.0.9",
      "com.codedx" %% "mapk" % "1.2.0",
    ),
    assembly / assemblyMergeStrategy := {
      case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
      case tastyFile()                                         => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
  )

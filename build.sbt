import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "score.discord",
      scalaVersion := "2.12.1",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "generalbot",
    resolvers += "jcenter-bintray" at "http://jcenter.bintray.com",
    libraryDependencies ++= Seq(
      scalaTest % Test,
      "net.dv8tion" % "JDA" % "3.0.0_200",
      "org.yaml" % "snakeyaml" % "1.18",
      "org.xerial" % "sqlite-jdbc" % "3.16.1",
      "com.typesafe.slick" %% "slick" % "3.2.0",
      "org.scala-lang.modules" %% "scala-async" % "0.9.6"
    )
  )

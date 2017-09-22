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
      "net.dv8tion" % "JDA" % "3.3.1_276",
      "org.apache.commons" % "commons-lang3" % "3.5",
      "org.xerial" % "sqlite-jdbc" % "3.16.1",
      "com.typesafe.slick" %% "slick" % "3.2.0",
      "com.typesafe" % "config" % "1.2.1",
      "org.scala-lang.modules" %% "scala-async" % "0.9.6"
    )
  )

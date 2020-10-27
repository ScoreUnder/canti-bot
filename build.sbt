import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "score.discord",
      scalaVersion := "2.13.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "canti-bot",
    resolvers += "jcenter-bintray" at "https://jcenter.bintray.com",
    libraryDependencies ++= Seq(
      scalaTest % Test,
      "net.dv8tion" % "JDA" % "4.2.0_211",
      "org.apache.commons" % "commons-lang3" % "3.11",
      "org.xerial" % "sqlite-jdbc" % "3.32.3.2",
      "com.typesafe.slick" %% "slick" % "3.3.3",
      "com.typesafe" % "config" % "1.4.1",
      "org.scala-lang.modules" %% "scala-async" % "1.0.0-M1",
      "com.google.re2j" % "re2j" % "1.5",
    ),
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case "module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
  )

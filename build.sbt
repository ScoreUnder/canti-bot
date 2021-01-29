import Dependencies._

enablePlugins(GitVersioning)

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "score.discord",
      scalaVersion := "2.13.3",
      git.useGitDescribe := true,
    )),
    name := "canti-bot",
    scalacOptions ++= List("-deprecation", "-unchecked", "-feature", "-Xasync", "-opt:inline,l:method", "-opt-inline-from:**"),
    resolvers += "jcenter-bintray" at "https://jcenter.bintray.com",
    libraryDependencies ++= Seq(
      scalaTest % Test,
      "net.dv8tion" % "JDA" % "4.2.0_227",
      "org.apache.commons" % "commons-lang3" % "3.11",
      "org.xerial" % "sqlite-jdbc" % "3.34.0",
      "com.typesafe.slick" %% "slick" % "3.3.3",
      "com.typesafe" % "config" % "1.4.1",
      "org.scala-lang.modules" %% "scala-async" % "1.0.0-M1",
      "com.google.re2j" % "re2j" % "1.5",
      "org.slf4j" % "slf4j-simple" % "1.7.30",
    ),
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case "module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
  )

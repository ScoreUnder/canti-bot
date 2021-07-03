import Dependencies._

enablePlugins(GitVersioning, ReproducibleBuildsPlugin)

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "score.discord",
      scalaVersion := "2.13.6",
      git.useGitDescribe := true,
    )),
    name := "canti-bot",
    scalacOptions ++= List("-deprecation", "-unchecked", "-feature", "-Xasync", "-opt:inline,l:method", "-opt-inline-from:**"),
    resolvers ++= Seq(
      "jcenter-bintray" at "https://jcenter.bintray.com",
      "m2-dv8tion" at "https://m2.dv8tion.net/releases",
    ),
    libraryDependencies ++= Seq(
      scalaTest % Test,
      ("net.dv8tion" % "JDA" % "4.3.0_285")
        .exclude("net.java.dev.jna", "jna")
        .exclude("club.minnced", "opus-java-api")
        .exclude("club.minnced", "opus-java-natives"),
      "org.xerial" % "sqlite-jdbc" % "3.36.0",
      "com.typesafe.slick" %% "slick" % "3.3.3",
      "com.typesafe" % "config" % "1.4.1",
      "org.scala-lang.modules" %% "scala-async" % "1.0.0-M1",
      "com.google.re2j" % "re2j" % "1.6",
      "org.slf4j" % "slf4j-simple" % "1.7.31",
    ),
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
  )

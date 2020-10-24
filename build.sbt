import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "score.discord",
      scalaVersion := "2.13.1",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "generalbot",
    resolvers += "jcenter-bintray" at "https://jcenter.bintray.com",
    libraryDependencies ++= Seq(
      scalaTest % Test,
      "net.dv8tion" % "JDA" % "4.1.1_155",
      "org.apache.commons" % "commons-lang3" % "3.5",
      "org.xerial" % "sqlite-jdbc" % "3.16.1",
      "com.typesafe.slick" %% "slick" % "3.3.2",
      "com.typesafe" % "config" % "1.2.1",
      "org.scala-lang.modules" %% "scala-async" % "0.10.0",
      // https://github.com/scala/scala-async/issues/220
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
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

import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.17"
  lazy val jda = ("net.dv8tion" % "JDA" % "5.0.2")
    .exclude("net.java.dev.jna", "jna")
    .exclude("club.minnced", "opus-java-api")
    .exclude("club.minnced", "opus-java-natives")
}

import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.10"
  lazy val jda = ("net.dv8tion" % "JDA" % "4.3.0_333")
    .exclude("net.java.dev.jna", "jna")
    .exclude("club.minnced", "opus-java-api")
    .exclude("club.minnced", "opus-java-natives")
}

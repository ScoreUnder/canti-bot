import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15"
  lazy val jda = ("net.dv8tion" % "JDA" % "4.4.1_353")
    .exclude("net.java.dev.jna", "jna")
    .exclude("club.minnced", "opus-java-api")
    .exclude("club.minnced", "opus-java-natives")
}

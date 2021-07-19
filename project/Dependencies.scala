import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.9"
  lazy val jda = ("net.dv8tion" % "JDA" % "4.3.0_298")
    .exclude("net.java.dev.jna", "jna")
    .exclude("club.minnced", "opus-java-api")
    .exclude("club.minnced", "opus-java-natives")
}

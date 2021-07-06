package score.discord.canti

import scala.util.control.NonFatal

object BotMeta {
  private def getPackageInfo(f: Package => String): Option[String] =
    try Option(f(getClass.getPackage))
    catch case NonFatal(_) => None

  lazy val NAME: String = getPackageInfo(_.getImplementationTitle).getOrElse("bot")
  lazy val VERSION: String = getPackageInfo(_.getImplementationVersion).getOrElse("unknown")
}

package score.discord.canti

import score.discord.canti.wrappers.NullWrappers.*

import scala.util.control.NonFatal

object BotMeta:
  private def getPackageInfo(f: Package => String | Null): Option[String] =
    try getClass.getPackage.?.flatMap(f(_).?)
    catch case NonFatal(_) => None

  lazy val NAME: String = getPackageInfo(_.getImplementationTitle).getOrElse("bot")
  lazy val VERSION: String = getPackageInfo(_.getImplementationVersion).getOrElse("unknown")

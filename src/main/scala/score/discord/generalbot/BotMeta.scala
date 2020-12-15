package score.discord.generalbot

import scala.util.Try
import scala.util.control.NonFatal

object BotMeta {
  lazy val VERSION: String = Try(getClass.getPackage.getImplementationVersion).recover { case NonFatal(_) => "unknown" }.get
}

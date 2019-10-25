package score.discord.generalbot.util

object IntStr {
  def unapply(arg: String): Option[Int] = arg.toIntOption
}

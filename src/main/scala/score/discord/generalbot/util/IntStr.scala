package score.discord.generalbot.util

/** Shim to allow pattern matching against stringified ints */
object IntStr {
  def unapply(arg: String): Option[Int] = arg.toIntOption
}

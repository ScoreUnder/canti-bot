package score.discord.canti.util

/** Shim to allow pattern matching against stringified ints */
object IntStr {
  def unapply(arg: String): Option[Int] = arg.toIntOption
}

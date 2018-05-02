package score.discord.generalbot.util

object MessageUtils {
  def blockMentionsNaive(message: String): String =
    message.replace("@", "@\u200C")

  private val FORMAT_MATCHER = s"(?<!\\\\)(\\*|_|~~|`)".r

  def escapeFormatting(message: String): String =
    FORMAT_MATCHER.replaceAllIn(message, "\\\\$1")
}

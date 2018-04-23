package score.discord.generalbot.util

object MessageUtils {
  def blockMentionsNaive(message: String): String =
    message.replace("@", "@\u200C")
}

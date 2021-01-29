package score.discord.canti.util

import net.dv8tion.jda.api.entities.Message

object StringUtils {
  def formatMessageForLog(message: Message): String =
    message.getContentRaw.split('\n').map("\t" + _).mkString("\n")
}

package score.discord.canti.util

import net.dv8tion.jda.api.entities.Message

object StringUtils:
  def formatMessageForLog(message: Message): String =
    message.getContentRaw.split('\n').map("\t" + _).mkString("\n")

  def trimToSize(s: String, chars: Int, lines: Int): String =
    val result = s.split('\n').take(lines).map(_.take(chars)).mkString("\n")
    if (result.length < s.length) s"""$result …""".stripMargin else result

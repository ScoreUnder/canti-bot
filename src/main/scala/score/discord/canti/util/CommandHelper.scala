package score.discord.canti.util

import net.dv8tion.jda.api.entities.{Guild, Member}
import net.dv8tion.jda.api.{JDA, entities}
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import score.discord.canti.wrappers.NullWrappers.*

object CommandHelper:
  private val mentionsRegex = """<(@&?|#)!?(-?\d+)>""".r

  /** Replace all mentions in the provided text with readable plaintext, for use outside of Discord.
    *
    * Not for sanitising mentions out of messages to be sent to Discord.
    *
    * @param guild
    *   optional guild to use in resolving members/channels/etc
    * @param input
    *   text to sanitise
    * @return
    *   sanitised text
    */
  def mentionsToPlaintext(guild: Option[Guild], message: String)(using jda: JDA): String =
    mentionsRegex.replaceSomeIn(
      message,
      { m =>
        val mentionType = m.group(1)
        for
          id <- m.group(2).toLongOption
          plaintext <-
            mentionType match
              case "#" => jda.getGuildChannelById(id).?.map(c => s"#${c.getName}")
              case "@" =>
                guild
                  .flatMap(_.getMemberById(id).?.map(m => s"@${m.getEffectiveName}"))
                  .orElse(jda.getUserById(id).?.map(u => s"@${u.getName}"))
              case "@&" => jda.getRoleById(id).?.map(r => s"@${r.getName}")
              case _    => throw IllegalStateException("Unexpected match in mentions regex")
        yield plaintext
      }
    )

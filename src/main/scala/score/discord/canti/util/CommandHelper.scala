package score.discord.canti.util

import net.dv8tion.jda.api.entities.{Guild, Member}
import net.dv8tion.jda.api.{JDA, MessageBuilder, entities}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.RichMessage.guild

object CommandHelper:
  /** Sanitise all mentions in the provided text for use with Discord.
    *
    * @param guild
    *   optional guild to use in resolving members/channels/etc
    * @param input
    *   text to sanitise
    * @return
    *   sanitised text
    */
  def mentionsToPlaintext(guild: Option[Guild], message: String)(using jda: JDA): String =
    import net.dv8tion.jda.api.entities.Message.MentionType.*
    val builder = MessageBuilder().append(message)
    guild match
      case Some(guild) =>
        builder.stripMentions(guild, USER, ROLE, CHANNEL)
      case None =>
        builder.stripMentions(jda, USER, ROLE, CHANNEL)
    builder.getStringBuilder.toString

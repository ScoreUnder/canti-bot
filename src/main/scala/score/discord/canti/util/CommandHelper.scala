package score.discord.canti.util

import net.dv8tion.jda.api.entities.{Guild, Member}
import net.dv8tion.jda.api.{JDA, entities}
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
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
  def mentionsToPlaintext(guild: Option[Guild], message: String)(using jda: JDA): String = ???
    /*import net.dv8tion.jda.api.entities.Message.MentionType.*
    val builder = MessageCreateBuilder().setContent(message).nn
    guild match
      case Some(guild) =>
        builder.stripMentions(guild, USER, ROLE, CHANNEL)
      case None =>
        builder.stripMentions(jda, USER, ROLE, CHANNEL)
    builder.getStringBuilder.nn.toString
    */
    // TODO: stripMentions is no longer part of the API

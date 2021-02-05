package score.discord.canti.wrappers.jda.matching

import net.dv8tion.jda.api.entities._
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.{MessageDeleteEvent, MessageReceivedEvent, MessageUpdateEvent}
import score.discord.canti.collections.MessageCache
import score.discord.canti.discord.BareMessage
import score.discord.canti.wrappers.jda.ID

object Events {

  object NonBotMessage {
    def unapply(ev: MessageReceivedEvent): Option[Message] =
      if (ev.isWebhookMessage || ev.getAuthor.isBot
        || ev.getMessage.getType != MessageType.DEFAULT)
        None
      else Some(ev.getMessage)
  }

  object NonBotMessageEdit {
    def unapply(ev: MessageUpdateEvent)(implicit messageCache: MessageCache): Option[(BareMessage, Message)] =
      if (ev.getAuthor.isBot || ev.getMessage.getType != MessageType.DEFAULT)
        None
      else
        messageCache.find(_.messageId.value == ev.getMessageIdLong) match {
          case None => None
          case Some(msg) if msg.text == ev.getMessage.getContentRaw => None // Not an edit
          case Some(msg) => Some((msg, ev.getMessage))
        }
  }

  object NonBotReact {
    def unapply(ev: MessageReactionAddEvent): Option[(MessageReaction, ID[Message], MessageChannel, User)] =
      if (ev.getUser.isBot) None
      else Some((ev.getReaction, new ID[Message](ev.getMessageIdLong), ev.getChannel, ev.getUser))
  }

  object MessageDelete {
    def unapply(ev: MessageDeleteEvent): Option[ID[Message]] =
      Some(new ID[Message](ev.getMessageIdLong))
  }

  object GuildVoiceUpdate {
    def unapply(ev: GuildVoiceUpdateEvent): Option[(Member, Option[VoiceChannel], Option[VoiceChannel])] =
      Some((ev.getEntity, Option(ev.getChannelLeft), Option(ev.getChannelJoined)))

    // To hint to the IDE what the name of each unapplied parameter is (ctrl+P in intelliJ)
    private def apply(member: Member, leftChannel: Option[VoiceChannel], joinedChannel: Option[VoiceChannel]) =
      throw new UnsupportedOperationException
  }

}
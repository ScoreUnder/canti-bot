package score.discord.canti.wrappers.jda.matching

import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.{
  MessageDeleteEvent, MessageReceivedEvent, MessageUpdateEvent
}
import score.discord.canti.collections.MessageCache
import score.discord.canti.discord.BareMessage
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichGenericMessageEvent.messageId

object Events:
  object NonBotMessage:
    def unapply(ev: MessageReceivedEvent): Option[Message] =
      if ev.isWebhookMessage || ev.getAuthor.isBot
        || ev.getMessage.getType != MessageType.DEFAULT
      then None
      else Some(ev.getMessage)

  object NonBotMessageEdit:
    def unapply(
      ev: MessageUpdateEvent
    )(using messageCache: MessageCache): Option[(BareMessage, Message)] =
      if ev.getAuthor.isBot || ev.getMessage.getType != MessageType.DEFAULT then None
      else
        messageCache.find(_.messageId.value == ev.getMessageIdLong) match
          case None                                                 => None
          case Some(msg) if msg.text == ev.getMessage.getContentRaw => None // Not an edit
          case Some(msg)                                            => Some((msg, ev.getMessage))

  object NonBotReact:
    def unapply(
      ev: MessageReactionAddEvent
    ): Option[(MessageReaction, ID[Message], MessageChannel, User)] =
      for
        user <- ev.getUser.?
        if !user.isBot
      yield (ev.getReaction, ev.messageId, ev.getChannel, user)

  object MessageDelete:
    def unapply(ev: MessageDeleteEvent): Option[ID[Message]] =
      Some(ev.messageId)

  object GuildVoiceUpdate:
    def unapply(
      ev: GuildVoiceUpdateEvent
    ): Option[(Member, Option[VoiceChannel], Option[VoiceChannel])] =
      Some((ev.getEntity, ev.getChannelLeft.?, ev.getChannelJoined.?))

    // To hint to the IDE what the name of each unapplied parameter is (ctrl+P in intelliJ)
    private def apply(
      member: Member,
      leftChannel: Option[VoiceChannel],
      joinedChannel: Option[VoiceChannel]
    ) =
      throw UnsupportedOperationException()

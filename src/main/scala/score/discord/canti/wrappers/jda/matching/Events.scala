package score.discord.canti.wrappers.jda.matching

import net.dv8tion.jda.api.entities.{Member, Message, MessageReaction, MessageType, User}
import net.dv8tion.jda.api.entities.channel.middleman.{AudioChannel, MessageChannel}
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
      val msg = ev.getMessage.nn
      if ev.isWebhookMessage || ev.getAuthor.nn.isBot
        || msg.getType != MessageType.DEFAULT
      then None
      else Some(msg)

  object NonBotMessageEdit:
    def unapply(ev: MessageUpdateEvent)(using
      messageCache: MessageCache
    ): Option[(BareMessage, Message)] =
      val editedMsg = ev.getMessage.nn
      if ev.getAuthor.nn.isBot || editedMsg.getType != MessageType.DEFAULT then None
      else
        messageCache.find(_.messageId == ev.messageId) match
          case None                                             => None
          case Some(msg) if msg.text == editedMsg.getContentRaw => None // Not an edit
          case Some(msg)                                        => Some((msg, editedMsg))

  object NonBotReact:
    def unapply(
      ev: MessageReactionAddEvent
    ): Option[(MessageReaction, ID[Message], MessageChannel, User)] =
      for
        user <- ev.getUser.?
        if !user.isBot
      yield (ev.getReaction.nn, ev.messageId, ev.getChannel.nn, user)

  object MessageDelete:
    def unapply(ev: MessageDeleteEvent): Tuple1[ID[Message]] =
      Tuple1(ev.messageId)

  object GuildVoiceUpdate:
    def unapply(ev: GuildVoiceUpdateEvent): (Member, Option[AudioChannel], Option[AudioChannel]) =
      (ev.getEntity.nn, ev.getChannelLeft.?, ev.getChannelJoined.?)

    // To hint to the IDE what the name of each unapplied parameter is (ctrl+P in intelliJ)
    private def apply(
      member: Member,
      leftChannel: Option[AudioChannel],
      joinedChannel: Option[AudioChannel]
    ) =
      throw UnsupportedOperationException()

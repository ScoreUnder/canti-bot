package score.discord.generalbot.wrappers.jda.matching

import net.dv8tion.jda.core.entities._
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.core.events.message.{MessageDeleteEvent, MessageReceivedEvent}
import score.discord.generalbot.wrappers.jda.ID

object Events {

  object NonBotMessage {
    def unapply(ev: MessageReceivedEvent): Option[Message] =
      if (ev.isWebhookMessage || ev.getAuthor.isBot
        || ev.getMessage.getType != MessageType.DEFAULT)
        None
      else Some(ev.getMessage)
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

}

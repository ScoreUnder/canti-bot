package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.util.APIHelper
import score.discord.generalbot.wrappers.jda.ID
import scala.concurrent.ExecutionContext.Implicits.global

class DeleteOwnedMessages(implicit messageOwnership: MessageOwnership) extends EventListener {
  override def onEvent(ev: Event) {
    ev match {
      case event: MessageReactionAddEvent =>
        event.getReaction.getEmote.getName match {
          case "❌" | "🚮" =>
            val messageId = new ID[Message](event.getMessageIdLong)
            messageOwnership(event.getJDA, messageId).foreach {
              case Some(user) if user == event.getUser =>
                APIHelper.tryRequest(
                  event.getChannel.deleteMessageById(event.getMessageId),
                  onFail = APIHelper.failure("deleting an owned message"))
              case Some(_) =>
                event.getReaction.removeReaction(event.getUser).queue()
              case None =>
            }
          case _ =>
        }
      case _ =>
    }
  }
}

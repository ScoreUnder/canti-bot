package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.core.entities.{ChannelType, Message}
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.util.APIHelper
import score.discord.generalbot.wrappers.jda.ID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteOwnedMessages(implicit messageOwnership: MessageOwnership) extends EventListener {
  private def getOwnership(event: MessageReactionAddEvent, messageId: ID[Message]) =
    if (event.getChannelType == ChannelType.PRIVATE)
      Future.successful(Some(event.getUser))
    else
      messageOwnership(event.getJDA, messageId)

  override def onEvent(ev: Event) {
    ev match {
      case event: MessageReactionAddEvent =>
        if (event.getUser.isBot) return

        event.getReactionEmote.getName match {
          case "❌" | "🚮" =>
            val messageId = new ID[Message](event.getMessageIdLong)
            getOwnership(event, messageId).foreach {
              case Some(user) if user == event.getUser =>
                APIHelper.tryRequest(
                  event.getChannel.deleteMessageById(event.getMessageId),
                  onFail = APIHelper.failure("deleting an owned message"))
                  .foreach(_ => messageOwnership.remove(messageId))
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

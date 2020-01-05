package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{ChannelType, Message, MessageChannel, User}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.generalbot.util.APIHelper
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.matching.Events.{MessageDelete, NonBotReact}
import score.discord.generalbot.wrappers.jda.matching.React

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteOwnedMessages(implicit messageOwnership: MessageOwnership) extends EventListener {
  private def getOwnership(user: User, channel: MessageChannel, messageId: ID[Message]) =
    if (channel.getType == ChannelType.PRIVATE)
      Future.successful(Some(user))
    else {
      implicit val jda: JDA = user.getJDA
      messageOwnership(messageId)
    }

  override def onEvent(ev: GenericEvent): Unit = {
    ev match {
      case NonBotReact(react @ React.Text("❌" | "🚮"), messageId, channel, user) =>
        getOwnership(user, channel, messageId).foreach {
          case Some(`user`) =>
            APIHelper.tryRequest(
              channel.deleteMessageById(messageId.value),
              onFail = APIHelper.failure("deleting an owned message"))
          case Some(_) =>
            react.removeReaction(user).queue()
          case None =>
        }
      case MessageDelete(message) =>
        messageOwnership.remove(message)
      case _ =>
    }
  }
}

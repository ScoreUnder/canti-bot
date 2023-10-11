package score.discord.canti.functionality.ownership

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Message, User}
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.canti.util.APIHelper
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichMessageChannel.deleteMessage
import score.discord.canti.wrappers.jda.matching.Events.{MessageDelete, NonBotReact}
import score.discord.canti.wrappers.jda.matching.React

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteOwnedMessages(using messageOwnership: MessageOwnership) extends EventListener:
  private def getOwnership(user: User, channel: MessageChannel, messageId: ID[Message]) =
    if channel.getType == ChannelType.PRIVATE then Future.successful(Some(user))
    else
      given JDA = user.getJDA.nn
      messageOwnership(messageId)

  override def onEvent(ev: GenericEvent): Unit =
    ev match
      case NonBotReact(react @ React.Text("❌" | "🚮"), messageId, channel, user) =>
        getOwnership(user, channel, messageId).foreach {
          case Some(`user`) =>
            channel.deleteMessage(messageId)
          case Some(_) =>
            react.removeReaction(user).nn.queue()
          case None =>
        }
      case MessageDelete(message) =>
        messageOwnership.remove(message)
      case _ =>

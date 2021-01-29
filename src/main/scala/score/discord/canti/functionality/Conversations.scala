package score.discord.canti.functionality

import net.dv8tion.jda.api.entities.{Message, MessageChannel, User}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.canti.wrappers.jda.Conversions._
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.matching.Events._

import scala.collection.concurrent.TrieMap

class Conversations extends EventListener {

  case class Conversation(message: Message,
                          private val userId: ID[User],
                          private val chanId: ID[MessageChannel]) {
    def next(action: Conversation => Unit): Unit = {
      start(userId, chanId)(action)
    }
  }

  private[this] val ongoingConversation = TrieMap[(ID[User], ID[MessageChannel]), Conversation => Unit]()

  def start(user: User, channel: MessageChannel)(action: Conversation => Unit): Unit = {
    start(user.id, channel.id)(action)
  }

  def start(user: ID[User], channel: ID[MessageChannel])(action: Conversation => Unit): Unit = {
    ongoingConversation((user, channel)) = action
  }

  override def onEvent(event: GenericEvent): Unit =
    event match {
      case NonBotMessage(msg) =>
        val userId = msg.getAuthor.id
        val chanId = msg.getChannel.id
        ongoingConversation
          .remove((userId, chanId))
          .foreach(_ (Conversation(msg, userId, chanId)))
      case _ =>
    }
}

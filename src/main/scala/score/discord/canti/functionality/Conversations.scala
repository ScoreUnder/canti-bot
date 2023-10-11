package score.discord.canti.functionality

import net.dv8tion.jda.api.entities.{Message, User}
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichSnowflake.id
import score.discord.canti.wrappers.jda.matching.Events.*

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, Promise}

class Conversations extends EventListener:
  private val ongoingConversation =
    TrieMap[(ID[User], ID[MessageChannel]), Promise[Message]]()

  def awaitMessage(user: ID[User], channel: ID[MessageChannel]): Future[Message] =
    val promise = Promise[Message]()
    ongoingConversation((user, channel)) = promise
    promise.future

  def awaitMessage(user: User, channel: MessageChannel): Future[Message] =
    awaitMessage(user.id, channel.id)

  override def onEvent(event: GenericEvent): Unit = event match
    case NonBotMessage(msg) =>
      val userId = msg.getAuthor.nn.id
      val chanId = msg.getChannel.nn.id
      ongoingConversation
        .remove((userId, chanId))
        .foreach(_.success(msg))
    case _ =>

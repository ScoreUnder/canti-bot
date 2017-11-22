package score.discord.generalbot.collections

import net.dv8tion.jda.core.entities.{MessageChannel, User}
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

class MessageCache extends EventListener {

  private final case class MessageData(
    chanId: ID[MessageChannel],
    senderId: ID[User],
    text: String
  )

  private val messages = new LogBuffer[MessageData](2000)

  def lastInChannelExcludingAuthor(channel: ID[MessageChannel], user: ID[User]): String = messages.synchronized {
    messages.view.filter(m => m.chanId == channel && m.senderId != user)
      .map(_.text)
      .find(_.nonEmpty)
      .getOrElse("")
  }

  def lastInChannelExcludingAuthor(channel: MessageChannel, user: User): String = lastInChannelExcludingAuthor(channel.id, user.id)

  override def onEvent(event: Event): Unit = {
    event match {
      case ev: MessageReceivedEvent =>
        val m = ev.getMessage
        messages ::= MessageData(m.getChannel.id, m.getAuthor.id, m.getRawContent)
      case _ =>
    }
  }
}

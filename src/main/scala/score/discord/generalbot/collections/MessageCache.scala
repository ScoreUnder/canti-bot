package score.discord.generalbot.collections

import net.dv8tion.jda.core.entities.{MessageChannel, User}
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

class MessageCache(capacity: Int = 2000) extends EventListener {

  final case class MessageData(
    chanId: ID[MessageChannel],
    senderId: ID[User],
    text: String
  )

  private val messages = new LogBuffer[MessageData](capacity)

  def find(pred: (MessageData) => Boolean): Option[MessageData] = messages.synchronized {
    messages find pred
  }

  override def onEvent(event: Event): Unit = {
    event match {
      case ev: MessageReceivedEvent =>
        val m = ev.getMessage
        messages ::= MessageData(m.getChannel.id, m.getAuthor.id, m.getContentRaw)
      case _ =>
    }
  }
}

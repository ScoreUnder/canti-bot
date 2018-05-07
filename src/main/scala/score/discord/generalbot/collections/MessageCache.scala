package score.discord.generalbot.collections

import net.dv8tion.jda.core.entities.{Message, MessageChannel, User}
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.{MessageReceivedEvent, MessageUpdateEvent}
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

class MessageCache(capacity: Int = 2000) extends EventListener {

  final case class MessageData(
    messageId: ID[Message],
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
        messages ::= MessageData(m.id, m.getChannel.id, m.getAuthor.id, m.getContentRaw)
      case ev: MessageUpdateEvent =>
        val msgId = ev.getMessage.id
        messages.findAndUpdate(_.messageId == msgId)(_.copy(text = ev.getMessage.getContentRaw))
      case _ =>
    }
  }
}

package score.discord.canti.collections

import net.dv8tion.jda.api.entities.{Message, MessageChannel}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.message.{MessageReceivedEvent, MessageUpdateEvent}
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.ErrorResponse.{UNKNOWN_CHANNEL, UNKNOWN_MESSAGE}
import score.discord.canti.discord.BareMessage
import score.discord.canti.util.APIHelper
import score.discord.canti.util.APIHelper.Error
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichMessageChannel.findMessage
import score.discord.canti.wrappers.jda.RichSnowflake.id

import scala.concurrent.ExecutionContext.Implicits.given
import scala.concurrent.Future

class MessageCache(capacity: Int = 2000) extends EventListener:

  private val messages = LogBuffer[BareMessage](capacity)

  def find(pred: BareMessage => Boolean): Option[BareMessage] = messages.synchronized {
    messages find pred
  }

  protected def toBareMessage(message: Message): BareMessage =
    BareMessage(message.id, message.getChannel.id, message.getAuthor.id, message.getContentRaw)

  def findOrRetrieve(channel: MessageChannel, id: ID[Message]): Future[Option[BareMessage]] =
    this find {
      _.messageId == id
    } match
      case Some(msg) => Future.successful(Some(msg))
      case None =>
        channel.findMessage(id)
          .map { msg => Some(toBareMessage(msg)) }
          .recover { case Error(UNKNOWN_CHANNEL | UNKNOWN_MESSAGE) =>
            None
          }

  override def onEvent(event: GenericEvent): Unit = event match
    case ev: MessageReceivedEvent =>
      val bareMessage = toBareMessage(ev.getMessage)
      messages.synchronized {
        messages ::= bareMessage
      }
    case ev: MessageUpdateEvent =>
      val msgId = ev.getMessage.id
      messages.synchronized {
        messages.findAndUpdate(_.messageId == msgId)(_.copy(text = ev.getMessage.getContentRaw))
      }
    case _ =>

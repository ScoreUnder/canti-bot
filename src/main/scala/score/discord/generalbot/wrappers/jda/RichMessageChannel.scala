package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.{Message, MessageChannel, User}
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class RichMessageChannel(val channel: MessageChannel) extends AnyVal {
  def name = channel.getName

  def unambiguousString = s"MessageChannel(${channel.id} /* $name */)"

  def !(message: MessageFromX): Future[Message] = channel.sendMessage(message.toMessage).queueFuture()

  def sendOwned(message: MessageFromX, owner: User)(implicit messageOwnership: MessageOwnership) =
    (this ! message).foreach(messageOwnership(_) = owner)

  def sendTemporary(message: MessageFromX, duration: Duration = 10 seconds)(implicit exec: Scheduler): Unit =
    channel.sendMessage(message.toMessage).queue({ (message) =>
      exec.schedule(duration) {
        message.delete().queue()
      }
    })
}

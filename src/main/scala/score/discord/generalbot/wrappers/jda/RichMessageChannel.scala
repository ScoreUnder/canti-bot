package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.MessageChannel
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.concurrent.duration._
import scala.language.postfixOps

class RichMessageChannel(val channel: MessageChannel) extends AnyVal {
  def name = channel.getName

  def unambiguousString = s"MessageChannel(${channel.id} /* $name */)"

  def !(message: MessageFromX): Unit = channel.sendMessage(message.toMessage).queue()

  def sendTemporary(message: MessageFromX, duration: Duration = 10 seconds)(implicit exec: Scheduler): Unit =
    channel.sendMessage(message.toMessage).queue({ (message) =>
      exec.schedule(duration) {
        message.delete().queue()
      }
    })
}

package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.MessageChannel
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions.MessageFromX

import scala.concurrent.duration._

class RichMessageChannel(val channel: MessageChannel) extends AnyVal {
  def name = channel.getName

  def id = channel.getIdLong

  def unambiguousString = s"MessageChannel($id /* $name */)"

  def !(message: MessageFromX): Unit = channel.sendMessage(message.toMessage).queue()

  def sendTemporary(message: MessageFromX, duration: Duration = 10 seconds)(implicit exec: Scheduler): Unit =
    channel.sendMessage(message.toMessage).queue({ (message) =>
      exec.schedule(duration) {
        message.delete().queue()
      }
    })
}

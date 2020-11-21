package score.discord.generalbot.command

import java.time.{Duration, Instant}

import net.dv8tion.jda.api.entities.Message
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.APIHelper
import score.discord.generalbot.util.TimeUtils.formatTimeDiff
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class PingCommand(implicit messageOwnership: MessageOwnership) extends Command.Anyone {
  override def name: String = "ping"

  override def aliases = Nil

  override def description: String = "Check the lag from the bot to the server"

  def getPingMessage(timeSent: Instant, timeReallySent: Option[Instant], timeOnServer: Instant, timeReceived: Instant): String = {
    var times = mutable.Buffer.empty[String]

    def diff(x: Instant, y: Instant) = formatTimeDiff(Duration.between(x, y))

    timeReallySent match {
      case Some(time) =>
        times += s"Time waited for rate limiting: ${diff(timeSent, time)}"
        times += s"Time from sending until received by server: ${diff(time, timeOnServer)}"
      case None =>
        times += s"Time from queueing until received by server: ${diff(timeSent, timeOnServer)}"
    }
    times += s"Time from reception on server until received by bot: ${diff(timeOnServer, timeReceived)}"
    for (time <- timeReallySent) {
      times += s"Total time (excl. rate limiting): ${diff(time, timeReceived)}"
    }
    times += s"Total time: ${diff(timeSent, timeReceived)}"
    times.mkString("\n")
  }

  override def execute(message: Message, args: String): Unit = {
    val timeSent = Instant.now()
    var timeReallySent: Option[Instant] = None
    (for {
      pingMessage <- message.reply(s"⏲ Checking ping...").mentionRepliedUser(false).setCheck({ () =>
        timeReallySent = Some(Instant.now())
        true
      }).queueFuture()
      timeOnServer = pingMessage.getTimeCreated.toInstant
      timeReceived = Instant.now()
      _ = messageOwnership(pingMessage) = message.getAuthor
      _ <- pingMessage.editMessage(getPingMessage(timeSent, timeReallySent, timeOnServer, timeReceived)).queueFuture()
    } yield ())
      .failed.foreach(APIHelper.loudFailure("checking ping", message.getChannel))
  }
}

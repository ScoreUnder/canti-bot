package score.discord.canti.command

import java.time.{Duration, Instant}

import net.dv8tion.jda.api.entities.Message
import score.discord.canti.collections.ReplyCache
import score.discord.canti.command.api.{
  ArgSpec, CommandInvocation, CommandPermissions, MessageInvoker
}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.util.TimeUtils.formatTimeDiff
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RetrievableMessage
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

class PingCommand(using messageOwnership: MessageOwnership, replyCache: ReplyCache)
    extends GenericCommand:
  override def name: String = "ping"

  override def description: String = "Check the lag from the bot to the server"

  override def permissions = CommandPermissions.Anyone

  override def argSpec = Nil

  def getPingMessage(
    timeSent: Instant,
    timeReallySent: Option[Instant],
    timeOnServer: Instant,
    timeReceived: Instant,
    gatewayPing: Long
  ): String =
    var times = mutable.Buffer.empty[String]

    def diff(x: Instant, y: Instant) = formatTimeDiff(Duration.between(x, y).nn)

    timeReallySent match
      case Some(time) =>
        times += s"Time waited for rate limiting: ${diff(timeSent, time)}"
        times += s"Time from sending until received by server: ${diff(time, timeOnServer)}"
      case None =>
        times += s"Time from queueing until received by server: ${diff(timeSent, timeOnServer)}"

    times += s"Time from reception on server until received by bot: ${diff(timeOnServer, timeReceived)}"
    for time <- timeReallySent do
      times += s"Total time (excl. rate limiting): ${diff(time, timeReceived)}"
    times += s"Total time: ${diff(timeSent, timeReceived)}"
    times += s"(Reported gateway ping: ${formatTimeDiff(Duration.ofMillis(gatewayPing).nn)})"
    times.mkString("\n")

  override def canBeEdited = false

  private def executeForMessage(message: Message) =
    def now() = Instant.now().nn
    val timeSent = now()
    var timeReallySent: Option[Instant] = None
    for
      pingMessage <- message
        .reply(s"⏲ Checking ping...")
        .mentionRepliedUser(false)
        .setCheck({ () =>
          timeReallySent = Some(now())
          true
        })
        .queueFuture()
      timeOnServer = pingMessage.getTimeCreated.toInstant.nn
      timeReceived = now()
      _ = messageOwnership(pingMessage) = message.getAuthor
      _ <- pingMessage
        .editMessage(
          getPingMessage(
            timeSent,
            timeReallySent,
            timeOnServer,
            timeReceived,
            message.getJDA.getGatewayPing
          )
        )
        .queueFuture()
    yield RetrievableMessage(pingMessage)

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    ctx.invoker match
      case MessageInvoker(message) => executeForMessage(message)
      case _ =>
        ctx.invoker.reply(
          BotMessages.error("This command only works with normal messages right now")
        )
end PingCommand

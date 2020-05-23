package score.discord.generalbot.wrappers.jda

import java.util.concurrent.ScheduledExecutorService

import net.dv8tion.jda.api.entities._
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class RichMessageChannel(val channel: MessageChannel) extends AnyVal {
  /** The name of this channel */
  def name = channel.getName

  /** A debug-friendly plaintext representation of this channel object */
  def unambiguousString = s"MessageChannel(${channel.rawId} /* $name */)"

  /** The mention string for this channel */
  def mention = s"<#${channel.rawId}>"

  /** Send a message to this channel.
    *
    * @param message message to send
    * @return the resulting Message, wrapped in Future
    */
  def !(message: MessageFromX): Future[Message] = channel.sendMessage(message.toMessage).queueFuture()

  /** Send a message to this channel, tracking it as owned by a specific user.
    *
    * @param message message to send
    * @param owner user who owns the message
    * @param messageOwnership message ownership cache
    * @return the resulting Message, wrapped in Future
    */
  def sendOwned(message: MessageFromX, owner: User)(implicit messageOwnership: MessageOwnership) = {
    val future = this ! message
    future.foreach(messageOwnership(_) = owner)
    future
  }

  /** Send a message to this channel, scheduling its deletion in the future.
    *
    * @param message message to send
    * @param duration time until deletion
    * @param exec task scheduler
    */
  def sendTemporary(message: MessageFromX, duration: Duration = 10 seconds)(implicit exec: Scheduler): Unit =
    channel.sendMessage(message.toMessage)
      .delay(duration)
      .flatMap { message => message.delete() }
      .queue()

  /** A list of all users in this channel */
  def participants: Seq[User] = channel match {
    case guildChannel: GuildChannel =>
      guildChannel.getMembers.iterator().asScala.map(_.getUser).toSeq
    case privateChannel: PrivateChannel =>
      List(channel.getJDA.getSelfUser, privateChannel.getUser)
  }
}

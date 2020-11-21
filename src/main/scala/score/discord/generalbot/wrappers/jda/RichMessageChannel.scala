package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.entities._
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class RichMessageChannel(val channel: MessageChannel) extends AnyVal {
  /** The name of this channel */
  def name: String = channel.getName

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
  def sendOwned(message: MessageFromX, owner: User)(implicit messageOwnership: MessageOwnership): Future[Message] = {
    val future = this ! message
    future.foreach(messageOwnership(_) = owner)
    future
  }

  /** A list of all users in this channel */
  def participants: Seq[User] = channel match {
    case guildChannel: GuildChannel =>
      guildChannel.getMembers.iterator().asScala.map(_.getUser).toSeq
    case privateChannel: PrivateChannel =>
      List(channel.getJDA.getSelfUser, privateChannel.getUser)
  }
}

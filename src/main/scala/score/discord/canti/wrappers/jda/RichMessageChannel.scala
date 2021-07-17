package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.requests.restaction.MessageAction
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper
import score.discord.canti.wrappers.jda.MessageConversions.MessageFromX
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichSnowflake.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

object RichMessageChannel:
  extension (channel: MessageChannel)
    /** The name of this channel */
    def name: String = channel.getName

    /** A debug-friendly plaintext representation of this channel object */
    def unambiguousString = s"MessageChannel(${channel.rawId} /* ${channel.name} */)"

    /** The mention string for this channel */
    def mention = s"<#${channel.rawId}>"

    /** Send a message to this channel.
      *
      * @param message
      *   message to send
      * @return
      *   the resulting Message, wrapped in Future
      */
    def !(message: MessageFromX): Future[Message] =
      channel.sendMessage(message.toMessage).queueFuture()

    /** Send a message to this channel, tracking it as owned by a specific user.
      *
      * @param message
      *   message to send
      * @param owner
      *   user who owns the message
      * @param messageOwnership
      *   message ownership cache
      * @return
      *   the resulting Message, wrapped in Future
      */
    def sendOwned(message: MessageFromX, owner: User)(using
      messageOwnership: MessageOwnership
    ): Future[Message] =
      val future = channel ! message
      future.foreach(messageOwnership(_) = owner)
      future

    /** A list of all users in this channel */
    def participants: Seq[User] = channel match
      case guildChannel: GuildChannel =>
        guildChannel.getMembers.iterator().nn.asScala.map(_.getUser).toSeq
      case privateChannel: PrivateChannel =>
        List(channel.getJDA.getSelfUser, privateChannel.getUser)

    def findMessage(messageId: ID[Message], logFail: Boolean = false): Future[Message] =
      val req = APIHelper.tryRequest(channel.retrieveMessageById(messageId.value))
      if logFail then
        req.failed.foreach(APIHelper.failure("retrieving a message"))
      req

    def editMessage(messageId: ID[Message], newMessage: Message, transform: MessageAction => MessageAction = identity): Future[Message] =
      APIHelper.tryRequest(
        transform(channel.editMessageById(messageId.value, newMessage)),
        onFail = APIHelper.failure("editing a message")
      )

    def deleteMessage(messageId: ID[Message]): Future[Unit] =
      APIHelper.tryRequest(
        channel.deleteMessageById(messageId.value),
        onFail = APIHelper.failure("deleting a message")
      ).map(_ => ())(using ExecutionContext.parasitic)

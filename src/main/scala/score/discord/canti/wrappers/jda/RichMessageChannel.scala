package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.middleman.{GuildChannel, MessageChannel}
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import net.dv8tion.jda.api.utils.messages.{MessageEditBuilder, MessageRequest}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper
import score.discord.canti.wrappers.jda.MessageConversions.MessageCreateFromX
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichSnowflake.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

object RichMessageChannel:
  extension (channel: MessageChannel)
    /** Send a message to this channel.
      *
      * @param message
      *   message to send
      * @return
      *   the resulting Message, wrapped in Future
      */
    def !(message: MessageCreateFromX): Future[Message] =
      channel.sendMessage(message.toMessageCreate).nn.queueFuture()

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
    def sendOwned(message: MessageCreateFromX, owner: User)(using
      messageOwnership: MessageOwnership
    ): Future[Message] =
      val future = channel ! message
      future.foreach(messageOwnership(_) = owner)
      future

    def findMessage(messageId: ID[Message], logFail: Boolean = false): Future[Message] =
      val req = APIHelper.tryRequest(channel.retrieveMessageById(messageId.value).nn)
      if logFail then req.failed.foreach(APIHelper.failure("retrieving a message"))
      req

    def editMessage(
      messageId: ID[Message],
      newMessage: Message,
      transform: MessageEditAction => MessageEditAction = identity,
    ): Future[Message] =
      val messageEdit = MessageEditBuilder.fromMessage(newMessage).nn
      APIHelper.tryRequest(
        transform(channel.editMessageById(messageId.value, messageEdit.build.nn).nn),
        onFail = APIHelper.failure("editing a message")
      )

    def deleteMessage(messageId: ID[Message]): Future[Unit] =
      APIHelper
        .tryRequest(
          channel.deleteMessageById(messageId.value).nn,
          onFail = APIHelper.failure("deleting a message")
        )
        .map(_ => ())(using ExecutionContext.parasitic)
  end extension
end RichMessageChannel

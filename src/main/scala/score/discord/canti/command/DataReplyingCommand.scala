package score.discord.canti.command

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.restaction.MessageAction
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichMessage.registerReply
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.scalaUtilChainingOps

trait DataReplyingCommand[T] extends ReplyingCommand:
  override final def executeAndGetMessage(message: Message, args: String): Future[Message] =
    executeAndGetMessageWithData(message, args).map(_._1)(ExecutionContext.parasitic)

  def executeAndGetMessageWithData(message: Message, args: String): Future[(Message, T)]

  override def executeFuture(message: Message, args: String): Future[Message] =
    for
      (replyUnsent, data) <- executeAndGetMessageWithData(message, args)
      reply <-
        message
          .reply(replyUnsent)
          .mentionRepliedUser(false)
          .pipe(tweakMessageAction(_, data))
          .queueFuture()
          .tap(message.registerReply)
    yield reply

  def tweakMessageAction(action: MessageAction, data: T): MessageAction

  override def executeForEdit(
    message: Message,
    myMessageOption: Option[ID[Message]],
    args: String
  ): Unit =
    for
      oldMessage <- myMessageOption; (myReply, data) <- executeAndGetMessageWithData(message, args)
    do
      APIHelper.tryRequest(
        message.getChannel
          .editMessageById(oldMessage.value, myReply)
          .pipe(tweakMessageAction(_, data)),
        onFail = APIHelper.failure("executing a command for edited message")
      )

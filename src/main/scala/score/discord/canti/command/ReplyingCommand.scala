package score.discord.canti.command

import net.dv8tion.jda.api.entities.Message
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichMessage.!

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

trait ReplyingCommand extends Command:
  given messageOwnership: MessageOwnership

  given replyCache: ReplyCache

  def executeAndGetMessage(message: Message, args: String): Future[Message]

  def executeFuture(message: Message, args: String): Future[Message] =
    for
      replyUnsent <- executeAndGetMessage(message, args)
      reply <- message ! replyUnsent
    yield reply

  override def execute(message: Message, args: String): Unit =
    executeFuture(message, args).failed.foreach(APIHelper.failure(s"executing the $name command"))

  override def executeForEdit(message: Message, myMessageOption: Option[ID[Message]], args: String): Unit =
    for oldMessage <- myMessageOption; myReply <- executeAndGetMessage(message, args) do
      APIHelper.tryRequest(message.getChannel.editMessageById(oldMessage.value, myReply),
        onFail = APIHelper.failure("executing a command for edited message"))

package score.discord.generalbot.command

import net.dv8tion.jda.api.entities.Message
import score.discord.generalbot.collections.ReplyCache
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.APIHelper
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ReplyingCommand extends Command {
  implicit def messageOwnership: MessageOwnership

  implicit def replyCache: ReplyCache

  def executeAndGetMessage(message: Message, args: String): Future[Message]

  def executeFuture(message: Message, args: String): Future[Message] =
    for {
      replyUnsent <- executeAndGetMessage(message, args)
      reply <- message reply replyUnsent
    } yield reply

  override def execute(message: Message, args: String): Unit =
    executeFuture(message, args)

  override def executeForEdit(message: Message, myMessageOption: Option[ID[Message]], args: String): Unit =
    for (oldMessage <- myMessageOption; myReply <- executeAndGetMessage(message, args)) {
      APIHelper.tryRequest(message.getChannel.editMessageById(oldMessage.value, myReply),
        onFail = APIHelper.failure("executing a command for edited message"))
    }
}

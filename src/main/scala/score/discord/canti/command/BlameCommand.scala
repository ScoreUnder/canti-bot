package score.discord.canti.command

import cps._
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.BotMessages
import score.discord.canti.wrappers.FutureEither._
import score.discord.canti.wrappers.jda.Conversions._
import score.discord.canti.wrappers.jda.ID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class BlameCommand(implicit val messageOwnership: MessageOwnership, val replyCache: ReplyCache) extends Command.Anyone with ReplyingCommand {
  override def name = "blame"

  override def description = "Reveal the user who caused the bot to send a given message"

  override def longDescription(invocation: String) =
    s"""Usage: `$invocation id` where `id` is the message ID to look up.
       |To get the ID, turn on Developer Mode, right click the message, and press "Copy ID".
     """.stripMargin

  override def executeAndGetMessage(message: Message, args: String): Future[Message] = {
    async {
      implicit val jda: JDA = message.getJDA
      val resultText = await(for {
        id <- Future.successful(
          Try(ID.fromString[Message](args)).fold(_ => Left("Expecting a message ID; got something else"), Right(_))
        ).flatView
        owner <- messageOwnership(id).map(_.toRight("No ownership info available for that message")).flatView
      } yield s"That message is owned by ${owner.mentionWithName}.")
      resultText.fold(BotMessages.error, BotMessages.plain).toMessage
    }
  }
}

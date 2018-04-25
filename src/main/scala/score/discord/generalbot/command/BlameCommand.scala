package score.discord.generalbot.command

import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.FutureEither._
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class BlameCommand(commands: Commands)(implicit messageOwnership: MessageOwnership) extends Command.Anyone {
  override def name = "blame"

  override def aliases = Nil

  override def description = "Reveal the user who caused the bot to send a given message"

  override val longDescription =
    s"""Usage: `${commands.prefix}$name id` where `id` is the message ID to look up.
       |To get the ID, turn on Developer Mode, right click the message, and press "Copy ID".
     """.stripMargin

  override def execute(message: Message, args: String): Unit = {
    async {
      val resultText = await(for {
        id <- Future.successful(
          Try(ID fromString args).fold(_ => Left("Expecting a message ID; got something else"), Right(_))
        ).flatView
        owner <- messageOwnership(message.getJDA, id).map(_.toRight("No ownership info available for that message")).flatView
      } yield s"That message is owned by ${owner.mentionWithName}.")
      message reply resultText.fold(BotMessages.error, BotMessages.plain)
    }.failed.map(APIHelper.loudFailure("getting a message's blame", message.getChannel))
  }
}

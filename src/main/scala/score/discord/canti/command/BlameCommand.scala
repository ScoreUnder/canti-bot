package score.discord.canti.command

import cps.*
import score.discord.canti.util.FutureAsyncMonadButGood
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import score.discord.canti.command.api.{ArgSpec, ArgType, CommandInvocation, CommandPermissions}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.BotMessages
import score.discord.canti.wrappers.FutureEither.*
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RetrievableMessage
import score.discord.canti.wrappers.jda.RichUser.mentionWithName

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try

class BlameCommand(using val messageOwnership: MessageOwnership, val replyCache: ReplyCache)
    extends GenericCommand:
  override def name = "blame"

  override def description = "Reveal the user who caused the bot to send a given message"

  override def longDescription(invocation: String) =
    s"""Usage: `$invocation id` where `id` is the message ID to look up.
       |To get the ID, turn on Developer Mode, right click the message, and press "Copy ID".
     """.stripMargin

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    async {
      given JDA = ctx.jda

      val resultText = await(for
        id <- Future.successful(ctx.args(messageID))
        owner <- messageOwnership(ID[Message](id))
          .map(_.toRight("No ownership info available for that message"))
          .flatView
      yield s"That message is owned by ${owner.mentionWithName}.")
      await(ctx.invoker.reply(resultText.fold(BotMessages.error, BotMessages.plain)))
    }

  private val messageID =
    ArgSpec("messageID", "The ID of the message (from this bot) to look up", ArgType.Integer)

  override def argSpec: List[ArgSpec[?]] = List(messageID)

  override def permissions = CommandPermissions.Anyone

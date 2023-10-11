package score.discord.canti.command.slash

import net.dv8tion.jda.api.entities.{Message, User}
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import score.discord.canti.collections.ReplyCache
import score.discord.canti.command.api.{ArgSpec, ArgType, CommandInvocation, CommandPermissions}
import score.discord.canti.command.GenericCommand
import score.discord.canti.functionality.SlashCommands
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.BotMessages
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.jda.{ID, RetrievableMessage}
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RichMessage.!
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

class RegisterGuildSlashCommandsCommand(owner: ID[User])(using Scheduler) extends GenericCommand:
  override def name: String = "regslash"

  override def description: String = "Register slash commands in guild (debug purposes)"

  override def longDescription(invocation: String): String =
    s"`${invocation} off` = remove them again"

  override val permissions = CommandPermissions.OneUserOnly(owner)

  private val offArg = ArgSpec(
    "add_or_remove",
    "The string 'off' to remove the commands",
    ArgType.GreedyString
      .withChoices(Choice("Add commands", "on"), Choice("Remove commands", "off")),
    required = false
  )

  override val argSpec = List(offArg)

  var slashCommands: Option[SlashCommands] = None

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    ctx.invoker.replyLater(transientIfPossible = true)
    slashCommands match
      case None => ctx.invoker.reply(BotMessages.error("No slash commands initialised"))
      case Some(slashCommands) =>
        val result =
          for member <- ctx.invoker.member
          yield
            val action = member.getGuild.nn.updateCommands().nn
            val offArgStr = ctx.args.get(offArg).getOrElse("")
            (if offArgStr == "off" then action else slashCommands.registerCommands(action))
              .queueFuture()
              .map { _ => BotMessages.okay("Registered commands!") }
              .flatMap(ctx.invoker.reply(_))
        result.fold(m => ctx.invoker.reply(BotMessages.error(m)), x => x)

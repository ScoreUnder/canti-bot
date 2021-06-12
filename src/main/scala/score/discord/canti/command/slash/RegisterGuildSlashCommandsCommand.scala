package score.discord.canti.command.slash

import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.command.{Command, ReplyingCommand}
import score.discord.canti.functionality.SlashCommands
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.BotMessages
import score.discord.canti.wrappers.jda.Conversions.{MessageFromEmbedBuilder, toRichRestAction}
import score.discord.canti.wrappers.jda.ID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RegisterGuildSlashCommandsCommand(val userId: ID[User], slashCommands: SlashCommands)(implicit val messageOwnership: MessageOwnership, val replyCache: ReplyCache) extends Command.OneUserOnly with ReplyingCommand {
  override def name: String = "regslash"

  override def description: String = "Register slash commands in guild (debug purposes)"

  override def longDescription(invocation: String): String = s"`${invocation} off` = remove them again"

  override def executeAndGetMessage(message: Message, args: String): Future[Message] = {
    val action = message.getGuild.updateCommands()
    (if (args.trim == "off") action else slashCommands.registerCommands(action))
      .queueFuture()
      .map { _ =>
        BotMessages.okay("Registered commands!").toMessage
      }
  }
}

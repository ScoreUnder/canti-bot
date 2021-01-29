package score.discord.canti.command

import net.dv8tion.jda.api.Permission._
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.{EmbedBuilder, JDA}
import score.discord.canti.BotMeta
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.Commands
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{BotMessages, IntStr}
import score.discord.canti.wrappers.jda.Conversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HelpCommand(commands: Commands)(implicit val messageOwnership: MessageOwnership, val replyCache: ReplyCache) extends Command.Anyone with ReplyingCommand {
  val pageSize = 10

  override def name = "help"

  override def aliases = List("h")

  override def description = "Show descriptions for all commands, or view one command in detail"

  override def executeAndGetMessage(message: Message, args: String): Future[Message] =
    Future {
      (args.trim match {
        case "" => showHelpPage(message, 1)
        case IntStr(page) => showHelpPage(message, page)
        case cmdName => showCommandHelp(cmdName)
      }).fold(BotMessages.error, identity).toMessage
    }

  private def inviteLink(implicit jda: JDA) =
    jda.getInviteUrl(
      MANAGE_ROLES, MANAGE_CHANNEL, MESSAGE_MANAGE, VOICE_MOVE_OTHERS
    )

  private def showCommandHelp(command: String) = {
    val unprefixed = command.stripPrefix(commands.prefix)
    commands.get(unprefixed)
      .toRight("Expected a page number or command name, but got something else.")
      .map(command => BotMessages plain
        s"""**Names:** `${(List(command.name) ++ command.aliases).mkString("`, `")}`
           |**Restrictions:** ${command.permissionMessage}
           |${command.description}
           |
           |${command.longDescription(commands.prefix + unprefixed)}""".stripMargin.trim)
  }

  private def showHelpPage(message: Message, page: Int) = {
    implicit val jda: JDA = message.getJDA
    val myCommands = commands.all.filter(_ checkPermission message)
    val pageOffset = pageSize * (page - 1)
    val numPages = (myCommands.length + pageSize - 1) / pageSize

    if (page < 1)
      Left("ಠ_ಠ")
    else if (page > numPages)
      Left(s"There are only $numPages pages, but you asked for page $page. That page does not exist.")
    else {
      val helpList = myCommands.slice(pageOffset, pageOffset + pageSize)
      val embed = new EmbedBuilder()
      embed.appendDescription("You can erase most replies this bot sends to you by reacting with ❌ or 🚮.\n" +
        s"**Commands (page $page of $numPages):**\n")

      for (command <- helpList) {
        embed appendDescription s"`${commands.prefix}${command.name}`: ${command.description}\n"
      }

      embed.appendDescription(
        "[Source code 🗒️](https://github.com/ScoreUnder/canti-bot) \\| " +
          s"[Invite to your server 📥]($inviteLink) \\| " +
          s"${BotMeta.NAME} ${BotMeta.VERSION}"
      )

      Right(embed)
    }
  }
}

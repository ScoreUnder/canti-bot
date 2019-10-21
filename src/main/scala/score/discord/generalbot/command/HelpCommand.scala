package score.discord.generalbot.command

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import score.discord.generalbot.collections.ReplyCache
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.BotMessages
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class HelpCommand(commands: Commands)(implicit val messageOwnership: MessageOwnership, val replyCache: ReplyCache) extends Command.Anyone with ReplyingCommand {
  val pageSize = 10

  override def name = "help"

  override def aliases = List("h")

  override def description = "Show descriptions for all commands, or view one command in detail"

  override def executeAndGetMessage(message: Message, args: String): Future[Message] =
    Future {
      ((args match {
        case "" => Some(1)
        case x => Try(x.toInt).toOption
      }) match {
        case Some(page) => showHelpPage(message, page)
        case None => showCommandHelp(args)
      }).fold(BotMessages.error, identity).toMessage
    }

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

      Right(embed)
    }
  }
}

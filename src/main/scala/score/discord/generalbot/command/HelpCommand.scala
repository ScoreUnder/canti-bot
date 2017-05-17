package score.discord.generalbot.command

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.BotMessages
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.util.Try

class HelpCommand(commands: Commands)(implicit exec: Scheduler, messageOwnership: MessageOwnership) extends Command.Anyone {
  val pageSize = 10

  override def name = "help"

  override def aliases = List("h")

  override def description = "Show descriptions for all commands, or view one command in detail"

  override def execute(message: Message, args: String) {
    val response = ((args match {
      case "" => Some(1)
      case x => Try(x.toInt).toOption
    }) match {
      case Some(page) => showHelpPage(message, page)
      case None => showCommandHelp(args)
    }).fold(BotMessages.error, identity)
    message.getChannel.sendOwned(response, message.getAuthor)
  }

  private def showCommandHelp(command: String) =
    commands.get(command.stripPrefix(commands.prefix))
      .toRight("Expected a page number or command name, but got something else.")
      .map(command => BotMessages plain
        s"""**Names:** `${(List(command.name) ++ command.aliases).mkString("`, `")}`
           |**Restrictions:** ${command.permissionMessage}
           |${command.description}
           |
           |${command.longDescription}""".stripMargin.trim)

  private def showHelpPage(message: Message, page: Int) = {
    val myCommands = commands.all.filter {
      cmd => cmd.checkPermission(message) && commands.isAllowedOnServer(cmd, message)
    }
    val pageOffset = pageSize * (page - 1)
    val numPages = (myCommands.length + pageSize - 1) / pageSize

    if (page < 1)
      Left("ಠ_ಠ")
    else if (page > numPages)
      Left(s"There are only $numPages pages, but you asked for page $page. That page does not exist.")
    else {
      val helpList = myCommands.slice(pageOffset, pageOffset + pageSize)
      val embed = new EmbedBuilder()
      embed.setTitle(s"Help (page $page of $numPages)", null)

      for (command <- helpList) {
        embed appendDescription s"`${command.name}`: ${command.description}\n"
      }

      Right(embed)
    }
  }
}

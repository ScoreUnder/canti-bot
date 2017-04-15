package score.discord.generalbot.command

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.util.BotMessages
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.util.Try

class HelpCommand(commands: Commands)(implicit exec: Scheduler) extends Command.Anyone {
  val pageSize = 10

  override def name = "help"

  override def aliases = Nil

  override def description = "Show descriptions for all commands, or view one command in detail"

  override def execute(message: Message, args: String) {
    ((args match {
      case "" => Some(1)
      case x => Try(x.toInt).toOption
    }) match {
      case Some(page) =>
        val pageOffset = pageSize * (page - 1)
        val numPages = (commands.length + pageSize - 1) / pageSize

        if (page < 1)
          Left("ಠ_ಠ")
        else if (page > numPages)
          Left(s"There are only $numPages pages, but you asked for page $page. That page does not exist.")
        else {
          val helpList = commands.names.slice(pageOffset, pageOffset + pageSize)
          val embed = new EmbedBuilder()
          embed.setTitle(s"Help (page $page of $numPages)", null)

          for (command <- helpList) {
            embed appendDescription s"`${command.name}`: ${command.description}\n"
          }

          Right(embed)
        }

      case None =>
        commands.get(args) match {
          case Some(command) => Right(BotMessages.plain(
            s"**Names:** `${(List(command.name) ++ command.aliases).mkString("`, `")}`\n" +
              s"**Restrictions:** ${command.permissionMessage}\n" +
              s"${command.description}\n\n${command.longDescription}"
          ))
          case None => Left("Expected a page number or command name, but got something else.")
        }
    }) match {
      case Left(msg) => message.getChannel ! BotMessages.error(msg)
      case Right(msg) => message.getChannel ! msg
    }
  }
}

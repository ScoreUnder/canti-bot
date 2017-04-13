package score.discord.generalbot.command

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.util.BotMessages
import score.discord.generalbot.wrappers.Conversions._

import scala.util.Try

class HelpCommand(commands: Commands) extends Command.Anyone {
  val pageSize = 10

  override def name = "help"

  override def aliases = Nil

  override def description = "Show descriptions for all commands"

  override def execute(message: Message, args: String) {
    message.getChannel ! ((args match {
      case "" => Some(1)
      case x => Try(x.toInt).toOption
    }) match {
      case Some(page) =>
        val pageOffset = pageSize * (page - 1)
        val numPages = (commands.length + pageSize - 1) / pageSize

        if (page < 1)
          BotMessages.error("ಠ_ಠ")
        else if (page > numPages)
          BotMessages.error(
            s"There are only $numPages pages, but you asked for page $page. That page does not exist."
          )
        else {
          val helpList = commands.names.slice(pageOffset, pageOffset + pageSize)
          val embed = new EmbedBuilder()
          embed.setTitle(s"Help (page $page of $numPages)", null)

          for (command <- helpList) {
            embed appendDescription s"`${command.name}`: ${command.description}\n"
          }

          embed
        }

      case None =>
        BotMessages.error("Expected a page number, but got something else.")
    })
  }
}

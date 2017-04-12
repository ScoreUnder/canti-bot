package score.discord.generalbot.command

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.{Message, MessageEmbed}
import score.discord.generalbot.GeneralBot
import score.discord.generalbot.functionality.Commands

import scala.util.Try

class HelpCommand(commands: Commands) extends Command {
  override def name = "help"

  override def aliases = Nil

  override def description = "Show descriptions for all commands"

  override def isAdminOnly = false

  override def execute(message: Message, args: String) {
    (args match {
      case "" => Some(1)
      case x => Try(x.toInt).toOption
    }) match {
      case Some(page) =>
        val pageOffset = 10 * (page - 1)
        val helpList = commands.keys.toList.sorted.slice(pageOffset, pageOffset + 10)
        val embed = new EmbedBuilder()
        embed.setTitle(s"Help (page $page)", null)

        for (commandName <- helpList) {
          embed.addField(commandName, commands(commandName).description, false)
        }

        message.getChannel.sendMessage(embed.build).queue()

      case None =>
        val embed = new EmbedBuilder()
        embed.setDescription("Expected a page number, but got something else.")
        embed.setColor(GeneralBot.ERROR_COLOR)
        message.getChannel.sendMessage(embed.build).queue()
    }
  }
}

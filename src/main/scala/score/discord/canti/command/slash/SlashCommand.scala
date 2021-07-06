package score.discord.canti.command.slash

import net.dv8tion.jda.api.interactions.commands.CommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.{CommandData, OptionData}
import score.discord.canti.command.BaseCommand

trait SlashCommand extends BaseCommand:
  def options: Seq[OptionData]

  def data = CommandData(name, description).addOptions(options: _*)

  def execute(origin: CommandInteraction): Unit

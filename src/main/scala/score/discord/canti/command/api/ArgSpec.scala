package score.discord.canti.command.api

import net.dv8tion.jda.api.interactions.commands.build.OptionData

final case class ArgSpec[T](
  name: String,
  description: String,
  argType: ArgType[T],
  required: Boolean = true
):
  def asJda = OptionData(argType.asJda, name, description, required)

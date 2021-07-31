package score.discord.canti.command.api

import net.dv8tion.jda.api.interactions.commands.build.OptionData
import score.discord.canti.wrappers.NullWrappers.*

import scala.annotation.threadUnsafe

final case class ArgSpec[T](
  name: String,
  description: String,
  argType: ArgType[T],
  required: Boolean = true
):
  @threadUnsafe lazy val asJda = OptionData(argType.asJda, name.lowernn, description, required)

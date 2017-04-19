package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.Channel
import score.discord.generalbot.wrappers.jda.Conversions._

class RichChannel(val channel: Channel) extends AnyVal {
  def name = channel.getName

  def unambiguousString = s"Channel(${channel.id} /* $name */)"
}

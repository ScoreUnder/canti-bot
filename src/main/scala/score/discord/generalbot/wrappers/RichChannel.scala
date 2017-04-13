package score.discord.generalbot.wrappers

import net.dv8tion.jda.core.entities.Channel

class RichChannel(val channel: Channel) extends AnyVal {
  def name = channel.getName
}

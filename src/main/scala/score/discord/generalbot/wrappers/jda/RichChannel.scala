package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.Channel

class RichChannel(val channel: Channel) extends AnyVal {
  def name = channel.getName

  def id = channel.getIdLong

  def unambiguousString = s"Channel($id /* $name */)"
}

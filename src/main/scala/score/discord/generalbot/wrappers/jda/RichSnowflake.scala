package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.ISnowflake

class RichSnowflake(val _me: ISnowflake) extends AnyVal {
  def id = _me.getIdLong
}

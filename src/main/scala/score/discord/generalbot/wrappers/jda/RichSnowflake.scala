package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.ISnowflake

class RichSnowflake[T <: ISnowflake](val _me: T) extends AnyVal {
  def rawId = _me.getIdLong
  def id = new ID[T](rawId)
}

package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.ISnowflake

class RichSnowflake[T <: ISnowflake](val _me: T) extends AnyVal {
  def id = _me.getIdLong
  def typedId = new ID[T](id)
}

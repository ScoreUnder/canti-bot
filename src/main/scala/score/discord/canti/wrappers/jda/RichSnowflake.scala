package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.ISnowflake

class RichSnowflake[T <: ISnowflake](val _me: T) extends AnyVal {
  /** The raw (Long) ID of this snowflake */
  def rawId = _me.getIdLong

  /** The [[ID]] of this snowflake */
  def id = new ID[T](rawId)
}

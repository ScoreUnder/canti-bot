package score.discord.canti

import net.dv8tion.jda.api.entities.ISnowflake

trait SnowflakeOrdering extends Ordered[ISnowflake]:
  this: ISnowflake =>
  override def compare(that: ISnowflake): Int =
    this.getIdLong - that.getIdLong match
      case x if x < 0 => -1
      case x if x > 0 => 1
      case _          => 0

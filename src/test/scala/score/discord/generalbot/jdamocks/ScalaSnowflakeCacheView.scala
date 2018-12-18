package score.discord.generalbot.jdamocks

import net.dv8tion.jda.core.entities.ISnowflake
import net.dv8tion.jda.core.utils.cache.SnowflakeCacheView

class ScalaSnowflakeCacheView[T >: Null <: ISnowflake](cache: collection.Map[Long, T], getName: T => String)
  extends ScalaCacheView[T](cache.values, getName) with SnowflakeCacheView[T] {
  override def getElementById(id: Long): T = cache.get(id).orNull
}

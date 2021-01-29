package score.discord.canti.jdamocks

import java.util.stream.{Stream => JStream}

import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.utils.cache.SortedSnowflakeCacheView

import scala.jdk.CollectionConverters._

class ScalaSnowflakeCacheView[Q <: Comparable[Q], T <: Q with ISnowflake](cache: collection.Map[Long, T], getName: T => String)
  extends ScalaCacheView[T](cache.values, getName) with SortedSnowflakeCacheView[T] {
  override def getElementById(id: Long): T = cache.getOrElse(id, null.asInstanceOf[T])

  override def streamUnordered(): JStream[T] = cache.values.asJavaCollection.stream()

  override def parallelStreamUnordered(): JStream[T] = cache.values.asJavaCollection.parallelStream()
}

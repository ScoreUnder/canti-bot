package score.discord.canti.jdamocks

import java.util.stream.Stream as JStream

import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.utils.cache.SortedSnowflakeCacheView

import scala.jdk.CollectionConverters.*

class ScalaSnowflakeCacheView[Q <: Comparable[Q], T <: Q & ISnowflake](
  cache: collection.Map[Long, T],
  getName: T => String
) extends ScalaCacheView[T](cache.values, getName)
    with SortedSnowflakeCacheView[T]:
  override def getElementById(id: Long): T | Null = cache.getOrElse(id, null)

  override def streamUnordered(): JStream[T] = cache.values.asJavaCollection.stream().nn

  override def parallelStreamUnordered(): JStream[T] =
    cache.values.asJavaCollection.parallelStream().nn

package score.discord.canti.jdamocks

import net.dv8tion.jda.api.entities.channel.{Channel, ChannelType}
import net.dv8tion.jda.api.utils.cache.ChannelCacheView

class ScalaChannelCacheView[T <: Channel](
  cache: collection.Map[Long, T],
  getName: T => String
) extends ScalaCacheView[T](cache.values, getName)
    with ChannelCacheView[T]:
  override def getElementById(id: Long): T | Null = cache.getOrElse(id, null)

  override def getElementById(channelType: ChannelType, id: Long): T | Null =
    cache.get(id).filter(_.getType == channelType).orNull

  override def ofType[C <: T](clazz: Class[C]): ChannelCacheView[C] =
    ScalaChannelCacheView[C](
      cache.collect { case (id, channel) if clazz.isInstance(channel) =>
        id -> clazz.cast(channel).nn
      },
      getName
    )

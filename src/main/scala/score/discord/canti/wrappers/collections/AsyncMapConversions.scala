package score.discord.canti.wrappers.collections

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Guild, User}
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import score.discord.canti.collections.AsyncMap
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.IdConversions.*
import score.discord.canti.wrappers.jda.RichSnowflake.id

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AsyncMapConversions:
  extension (me: AsyncMap[(ID[Guild], ID[AudioChannel]), ID[User]])
    def apply(channel: AudioChannel): Future[Option[User]] =
      given JDA = channel.getJDA.nn
      me.get(key(channel)).map(_.flatMap(_.find))

    def update(channel: AudioChannel, user: User): Future[Int] =
      me(key(channel)) = user.id

    def remove(channel: AudioChannel): Future[Int] = me.remove(key(channel))

  private def key(channel: AudioChannel): (ID[Guild], ID[AudioChannel]) =
    (channel.getGuild.nn.id, channel.id)

package score.discord.canti.wrappers.collections

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Guild, User, VoiceChannel}
import score.discord.canti.collections.AsyncMap
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.IdConversions.*
import score.discord.canti.wrappers.jda.RichSnowflake.id

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AsyncMapConversions:
  extension (me: AsyncMap[(ID[Guild], ID[VoiceChannel]), ID[User]])
    def apply(channel: VoiceChannel): Future[Option[User]] =
      given JDA = channel.getJDA
      me.get(key(channel)).map(_.flatMap(_.find))

    def update(channel: VoiceChannel, user: User): Future[Int] =
      me(key(channel)) = user.id

    def remove(channel: VoiceChannel): Future[Int] = me.remove(key(channel))

  private def key(channel: VoiceChannel): (ID[Guild], ID[VoiceChannel]) =
    (channel.getGuild.id, channel.id)

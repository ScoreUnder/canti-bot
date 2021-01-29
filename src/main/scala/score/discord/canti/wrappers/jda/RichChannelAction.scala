package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{GuildChannel, IPermissionHolder}
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import score.discord.canti.discord.permissions.PermissionCollection

import scala.jdk.CollectionConverters._

class RichChannelAction[T <: GuildChannel](val _me: ChannelAction[T]) extends AnyVal {
  def applyPerms(perms: PermissionCollection[IPermissionHolder]): ChannelAction[T] = {
    perms.values.foreach { case (k, v) => _me.addPermissionOverride(k, v.allows.asJava, v.denies.asJava) }
    _me
  }
}

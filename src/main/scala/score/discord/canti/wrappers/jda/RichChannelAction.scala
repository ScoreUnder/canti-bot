package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import score.discord.canti.discord.permissions.{PermissionCollection, PermissionHolder}
import score.discord.canti.wrappers.NullWrappers.*

import scala.jdk.CollectionConverters.*

object RichChannelAction:
  private val logger = loggerOf[this.type]

  extension [T <: GuildChannel](_me: ChannelAction[T])
    def applyPerms(perms: PermissionCollection[PermissionHolder]): ChannelAction[T] =
      import PermissionHolder.*
      perms.values.foreach {
        case (Role(k), v) =>
          _me.addRolePermissionOverride(k.value, v.allows.asJava, v.denies.asJava)
        case (Member(k), v) =>
          _me.addMemberPermissionOverride(k.value, v.allows.asJava, v.denies.asJava)
        case (Unknown(k), v) => logger.warn("Unknown permission holder being applied")
      }
      _me

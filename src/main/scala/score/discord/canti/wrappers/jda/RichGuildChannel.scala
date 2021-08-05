package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{GuildChannel, IPermissionHolder}
import net.dv8tion.jda.api.managers.ChannelManager
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.discord.permissions.{
  PermissionAttachment, PermissionCollection, PermissionHolder
}

import scala.jdk.CollectionConverters.*

object RichGuildChannel:
  private val logger = loggerOf[this.type]

  extension (_me: GuildChannel)
    def permissionAttachments: PermissionCollection[PermissionHolder] =
      PermissionCollection(_me.getPermissionOverrides.asScala.map { ov =>
        PermissionHolder(ov) -> PermissionAttachment(ov)
      }.toSeq)

    def getPermissionAttachment(what: IPermissionHolder): PermissionAttachment =
      _me.getPermissionOverride(what).?.fold(PermissionAttachment.empty)(PermissionAttachment.apply)

    def applyPerms(perms: PermissionCollection[?]): ChannelManager =
      import PermissionHolder.*
      val manager = _me.getManager
      perms.values.foreach {
        case (Role(k), v) =>
          manager.putRolePermissionOverride(k.value, v.allows.asJava, v.denies.asJava)
        case (Member(k), v) =>
          manager.putMemberPermissionOverride(k.value, v.allows.asJava, v.denies.asJava)
        case (Unknown(k), v) => logger.warn("Unknown permission holder being applied")
      }
      manager

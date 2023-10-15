package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.IPermissionHolder
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import score.discord.canti.discord.permissions.{
  PermissionAttachment, PermissionCollection, PermissionHolder
}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.RichSnowflake.rawId

import scala.jdk.CollectionConverters.*
import net.dv8tion.jda.api.managers.channel.attribute.IPermissionContainerManager

object RichGuildChannel:
  private val logger = loggerOf[this.type]

  extension (_me: GuildChannel)
    def permissionAttachments: PermissionCollection[PermissionHolder] =
      PermissionCollection(
        _me.getPermissionContainer.nn.getPermissionOverrides.nn.asScala.map { ov =>
          PermissionHolder(ov) -> PermissionAttachment(ov)
        }.toSeq
      )

    def getPermissionAttachment(what: IPermissionHolder): PermissionAttachment =
      _me.getPermissionContainer.nn
        .getPermissionOverride(what)
        .?
        .fold(PermissionAttachment.empty)(PermissionAttachment.apply)

    def applyPerms(perms: PermissionCollection[?]): IPermissionContainerManager[?, ?] =
      import PermissionHolder.*
      val manager = _me.getPermissionContainer.nn.getManager.nn
      perms.values.foreach {
        case (Role(k), v) =>
          manager.putRolePermissionOverride(k.value, v.allows.asJava, v.denies.asJava)
        case (Member(k), v) =>
          manager.putMemberPermissionOverride(k.value, v.allows.asJava, v.denies.asJava)
        case (Unknown(k), v) => logger.warn("Unknown permission holder being applied")
      }
      manager

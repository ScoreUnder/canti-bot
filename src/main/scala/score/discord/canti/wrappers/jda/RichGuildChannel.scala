package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{GuildChannel, IPermissionHolder}
import net.dv8tion.jda.api.managers.ChannelManager
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.discord.permissions.{PermissionAttachment, PermissionCollection}

import scala.jdk.CollectionConverters.*

object RichGuildChannel:
  extension (_me: GuildChannel)
    def permissionAttachments: PermissionCollection[IPermissionHolder] =
      PermissionCollection(_me.getPermissionOverrides.asScala.flatMap { ov =>
        ov.getPermissionHolder.?.map(
          _ -> PermissionAttachment(ov)
        ) // TODO: find better solution (this null-safety discards all uncached entities completely)
      }.toSeq)

    def getPermissionAttachment(what: IPermissionHolder): PermissionAttachment =
      _me.getPermissionOverride(what).?.fold(PermissionAttachment.empty)(PermissionAttachment.apply)

    def applyPerms(perms: PermissionCollection[IPermissionHolder]): ChannelManager =
      val manager = _me.getManager
      perms.values.foreach { case (k, v) =>
        manager.putPermissionOverride(k, v.allows.asJava, v.denies.asJava)
      }
      manager

package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.entities.{GuildChannel, IPermissionHolder}
import net.dv8tion.jda.api.managers.ChannelManager
import score.discord.generalbot.discord.permissions.{PermissionAttachment, PermissionCollection}

import scala.jdk.CollectionConverters._

class RichGuildChannel(val _me: GuildChannel) extends AnyVal {
  def permissionAttachments: PermissionCollection[IPermissionHolder] =
    PermissionCollection(_me.getPermissionOverrides.asScala.map { ov =>
      ov.getPermissionHolder -> PermissionAttachment(ov)
    }.toSeq)

  def getPermissionAttachment(what: IPermissionHolder): PermissionAttachment =
    Option(_me.getPermissionOverride(what)).fold(PermissionAttachment.empty)(PermissionAttachment.apply)

  def applyPerms(perms: PermissionCollection[IPermissionHolder]): ChannelManager = {
    val manager = _me.getManager
    perms.values.foreach { case (k, v) => manager.putPermissionOverride(k, v.allows.asJava, v.denies.asJava) }
    manager
  }
}

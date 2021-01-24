package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.entities.{GuildChannel, IPermissionHolder}
import score.discord.generalbot.discord.permissions.{PermissionAttachment, PermissionCollection}

import scala.jdk.CollectionConverters._

class RichGuildChannel(val _me: GuildChannel) extends AnyVal {
  def permissionOverrides: PermissionCollection[IPermissionHolder] =
    PermissionCollection(_me.getPermissionOverrides.asScala.map { ov =>
      ov.getPermissionHolder -> PermissionAttachment(ov.getAllowed.asScala.toSet, ov.getDenied.asScala.toSet)
    }.toSeq)
}

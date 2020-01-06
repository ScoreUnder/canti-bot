package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.{GuildChannel, Member}
import score.discord.generalbot.wrappers.jda.ChannelPermissionUpdater.PermissionState
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.concurrent.Future

class ChannelPermissionUpdater(channel: GuildChannel) {
  private[this] val overrideState = mutable.HashMap[Member, PermissionState](
    channel.getMemberPermissionOverrides.asScala
      .map(ov => ov.getMember -> PermissionState(ov.getAllowedRaw, ov.getDeniedRaw))
      .toSeq: _*
  ).withDefaultValue(PermissionState(0, 0))
  private val manager = channel.getManager

  def grant(member: Member, perms: Permission*): this.type = {
    val permsRaw = perms.map(_.getRawValue).fold(0L)(_ | _)
    val oldOverride = overrideState(member)
    val newOverride = oldOverride.copy(
      allow = oldOverride.allow | permsRaw,
      deny = oldOverride.deny & ~permsRaw)
    manager.putPermissionOverride(member, newOverride.allow, newOverride.deny)
    overrideState(member) = newOverride
    this
  }

  def queue(): Future[Void] = {
    manager.queueFuture()
  }
}

object ChannelPermissionUpdater {

  private case class PermissionState(allow: Long, deny: Long)

  def apply(channel: GuildChannel) = new ChannelPermissionUpdater(channel)
}

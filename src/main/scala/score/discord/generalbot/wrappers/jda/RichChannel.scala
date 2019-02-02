package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.{Channel, Member}
import net.dv8tion.jda.core.requests.restaction.PermissionOverrideAction
import score.discord.generalbot.wrappers.Tap._
import score.discord.generalbot.wrappers.jda.Conversions._

class RichChannel(val channel: Channel) extends AnyVal {
  def name = channel.getName

  def unambiguousString = s"Channel(${channel.rawId} /* $name */)"

  def mention = s"<#${channel.rawId}>"

  /** Creates a permission override on this channel for the specified member,
    * using the old permissions if an applicable override already exists.
    *
    * @param member member to which the permissions apply
    * @return permission override request (not yet sent)
    */
  def updateOrCreatePermissionOverride(member: Member): PermissionOverrideAction =
    channel.putPermissionOverride(member).tap { permOverride =>
      Option(channel.getPermissionOverride(member)).foreach { oldPerms =>
        permOverride.setAllow(oldPerms.getAllowedRaw)
        permOverride.setDeny(oldPerms.getDeniedRaw)
      }
    }
}

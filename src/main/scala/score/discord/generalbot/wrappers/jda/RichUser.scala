package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.client.entities.Group
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.{Channel, User}
import score.discord.generalbot.util.MessageUtils
import score.discord.generalbot.wrappers.jda.Conversions._

class RichUser(val me: User) extends AnyVal {
  def name = me.getName

  def discriminator = me.getDiscriminator

  def mention = me.getAsMention

  def mentionAsText = s"@${me.getName}#${me.getDiscriminator}"

  def mentionWithName = {
    val fullName = MessageUtils.sanitise(mentionAsText)
    s"$mention ($fullName)"
  }

  def unambiguousString = s"User(${me.rawId} /* $name#$discriminator */)"

  def canSee(channel: Channel): Boolean =
    Option(channel.getGuild.getMember(me))
      .exists(_.hasPermission(channel, Permission.MESSAGE_READ))

  def canSee(group: Group): Boolean =
    group.getUserCache.getElementById(me.getIdLong) ne null
}

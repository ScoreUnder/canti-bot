package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.{GuildChannel, User}
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

  def canSee(channel: GuildChannel): Boolean =
    Option(channel.getGuild.getMember(me))
      .exists(_.hasPermission(channel, Permission.MESSAGE_READ))
}

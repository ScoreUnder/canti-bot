package score.discord.generalbot.util

import net.dv8tion.jda.core.entities.Member
import score.discord.generalbot.wrappers.jda.Conversions._

case class GuildUserId(guild: Long, user: Long)

object GuildUserId {
  def apply(member: Member): GuildUserId = GuildUserId(member.getGuild.id, member.getUser.id)
}

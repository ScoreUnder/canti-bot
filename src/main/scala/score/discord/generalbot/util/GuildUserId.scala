package score.discord.generalbot.util

import net.dv8tion.jda.core.entities.Member

case class GuildUserId(guild: Long, user: Long)

object GuildUserId {
  def apply(member: Member): GuildUserId = GuildUserId(member.getGuild.getIdLong, member.getUser.getIdLong)
}

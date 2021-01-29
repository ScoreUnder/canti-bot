package score.discord.canti.util

import net.dv8tion.jda.api.entities.{Guild, Member, User}
import score.discord.canti.wrappers.jda.Conversions._
import score.discord.canti.wrappers.jda.ID

/** A combined Guild and User ID pair */
case class GuildUserId(guild: ID[Guild], user: ID[User])

object GuildUserId {
  def apply(member: Member): GuildUserId = GuildUserId(member.getGuild.id, member.getUser.id)
}

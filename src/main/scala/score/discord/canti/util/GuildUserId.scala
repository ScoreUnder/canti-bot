package score.discord.canti.util

import net.dv8tion.jda.api.entities.{Guild, Member, User}
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichSnowflake.id

/** A combined Guild and User ID pair */
case class GuildUserId(guild: ID[Guild], user: ID[User])

object GuildUserId:
  def apply(member: Member): GuildUserId =
    import scala.language.unsafeNulls  // getGuild/getUser are both non-null anyway
    GuildUserId(member.getGuild.id, member.getUser.id)

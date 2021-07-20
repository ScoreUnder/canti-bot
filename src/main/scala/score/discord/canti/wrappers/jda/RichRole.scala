package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.Role
import score.discord.canti.wrappers.jda.RichSnowflake.rawId

object RichRole:
  extension (role: Role)
    /** The mention string for this role */
    inline def mention = role.getAsMention

    def unambiguousString = s"Role(${role.rawId} /* ${role.getName} */)"

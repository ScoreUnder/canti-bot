package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.Role

object RichRole:
  extension (role: Role)
    /** The mention string for this role */
    inline def mention = role.getAsMention

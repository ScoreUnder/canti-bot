package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.ISnowflake

object RichSnowflake:
  extension (_me: ISnowflake)
    /** The raw (Long) ID of this snowflake */
    inline def rawId = _me.getIdLong

    /** The [[ID]] of this snowflake */
    inline def id = ID[_me.type](_me.rawId)

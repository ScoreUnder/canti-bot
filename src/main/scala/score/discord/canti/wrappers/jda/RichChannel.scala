package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.channel.Channel
import score.discord.canti.wrappers.jda.RichSnowflake.rawId

object RichChannel:
  extension (_me: Channel)
    /** The name of this guild channel */
    inline def name: String = _me.getName.nn

    /** A debug-friendly plaintext representation of this guild channel object */
    def unambiguousString: String = s"Channel(${_me.rawId} /* ${_me.name} */)"

    /** The mention string for this guild channel */
    def mention: String = s"<#${_me.rawId}>"

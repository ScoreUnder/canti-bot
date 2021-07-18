package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.VoiceChannel
import score.discord.canti.wrappers.jda.RichSnowflake.rawId

object RichVoiceChannel:
  extension (channel: VoiceChannel)
    /** The name of this voice channel */
    inline def name = channel.getName

    /** A debug-friendly plaintext representation of this voice channel object */
    def unambiguousString = s"Channel(${channel.rawId} /* ${channel.name} */)"

    /** The mention string for this voice channel */
    def mention = s"<#${channel.rawId}>"

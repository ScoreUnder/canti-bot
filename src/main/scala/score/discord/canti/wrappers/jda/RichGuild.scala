package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.*
import score.discord.canti.wrappers.jda.RichSnowflake.rawId

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object RichGuild:
  extension (guild: Guild)
    /** The name of this guild */
    def name = guild.getName

    /** A debug-friendly plaintext representation of this guild object */
    def unambiguousString = s"Guild(${guild.rawId} /* ${guild.name} */)"

    /** A list of all voice states in this guild */
    def voiceStates: mutable.Buffer[GuildVoiceState] = guild.getVoiceStates.asScala

    /** Find a voice channel within this guild by ID.
      *
      * @param channel ID of the channel to find
      * @return the channel, optionally (if found)
      */
    def findVoiceChannel(channel: ID[VoiceChannel]): Option[VoiceChannel] = Option(guild.getVoiceChannelById(channel.value))

    /** Find a role within this guild by ID.
      *
      * @param role ID of the role to find
      * @return the role, optionally (if found)
      */
    def findRole(role: ID[Role]): Option[Role] = Option(guild.getRoleById(role.value))

    /** Find a member within this guild, given its user counterpart.
      *
      * @param user the user corresponding to the member to find
      * @return the member, optionally (if found)
      */
    def findMember(user: User): Option[Member] = Option(guild.getMember(user))

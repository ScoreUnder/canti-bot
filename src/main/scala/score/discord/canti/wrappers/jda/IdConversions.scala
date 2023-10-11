package score.discord.canti.wrappers.jda

import scala.annotation.targetName
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Guild, Member, Role, User}
import score.discord.canti.wrappers.NullWrappers.*
import net.dv8tion.jda.api.entities.channel.concrete.{TextChannel, VoiceChannel}
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel

object IdConversions:
  extension (me: ID[MessageChannel])
    @targetName("find_ID_MessageChannel")
    // TODO: test if this works for DMs
    def find(using jda: JDA): Option[MessageChannel] = jda.getTextChannelById(me.value).?

  extension (me: ID[Guild])
    @targetName("find_ID_Guild")
    def find(using jda: JDA): Option[Guild] = jda.getGuildById(me.value).?

  extension (me: ID[VoiceChannel])
    @targetName("find_ID_VoiceChannel")
    def find(using jda: JDA): Option[VoiceChannel] = jda.getVoiceChannelById(me.value).?

  extension (me: ID[TextChannel])
    @targetName("find_ID_TextChannel")
    def find(using jda: JDA): Option[TextChannel] = jda.getTextChannelById(me.value).?

  extension (me: ID[User])
    @targetName("find_ID_User")
    def find(using jda: JDA): Option[User] = jda.getUserById(me.value).?

  extension (me: ID[Member])
    @targetName("find_ID_Member")
    def find(guild: Guild): Option[Member] = guild.getMemberById(me.value).?

  extension (me: ID[Role])
    @targetName("find_ID_Role")
    def find(using jda: JDA): Option[Role] = jda.getRoleById(me.value).?

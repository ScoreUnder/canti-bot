package score.discord.canti.wrappers.jda

import scala.annotation.targetName
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{
  Guild, Member, MessageChannel, Role, TextChannel, User, VoiceChannel
}

object IdConversions:
  extension (me: ID[MessageChannel])
    @targetName("find_ID_MessageChannel")
    def find(using jda: JDA): Option[MessageChannel] =
      Option(jda.getTextChannelById(me.value)).orElse(Option(jda.getPrivateChannelById(me.value)))

  extension (me: ID[Guild])
    @targetName("find_ID_Guild")
    def find(using jda: JDA): Option[Guild] = Option(jda.getGuildById(me.value))

  extension (me: ID[VoiceChannel])
    @targetName("find_ID_VoiceChannel")
    def find(using jda: JDA): Option[VoiceChannel] = Option(jda.getVoiceChannelById(me.value))

  extension (me: ID[TextChannel])
    @targetName("find_ID_TextChannel")
    def find(using jda: JDA): Option[TextChannel] = Option(jda.getTextChannelById(me.value))

  extension (me: ID[User])
    @targetName("find_ID_User")
    def find(using jda: JDA): Option[User] = Option(jda.getUserById(me.value))

  extension (me: ID[Member])
    @targetName("find_ID_Member")
    def find(guild: Guild): Option[Member] = Option(guild.getMemberById(me.value))

  extension (me: ID[Role])
    @targetName("find_ID_Role")
    def find(using jda: JDA): Option[Role] = Option(jda.getRoleById(me.value))

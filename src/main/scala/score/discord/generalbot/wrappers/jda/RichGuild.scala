package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.entities._
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.jdk.CollectionConverters._

class RichGuild(val guild: Guild) extends AnyVal {
  def name = guild.getName

  def unambiguousString = s"Guild(${guild.rawId} /* $name */)"

  def voiceStates = guild.getVoiceStates.asScala

  def findVoiceChannel(channel: ID[VoiceChannel]) = Option(guild.getVoiceChannelById(channel.value))

  def findRole(role: ID[Role]) = Option(guild.getRoleById(role.value))

  def findMember(user: User): Option[Member] = Option(guild.getMember(user))
}

package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.Guild

import scala.collection.JavaConverters._

class RichGuild(val guild: Guild) extends AnyVal {
  def name = guild.getName

  def id = guild.getIdLong

  def unambiguousString = s"Guild($id /* $name */)"

  def voiceStates = guild.getVoiceStates.asScala
}

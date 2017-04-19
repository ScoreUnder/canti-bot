package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.Guild
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.collection.JavaConverters._

class RichGuild(val guild: Guild) extends AnyVal {
  def name = guild.getName

  def unambiguousString = s"Guild(${guild.id} /* $name */)"

  def voiceStates = guild.getVoiceStates.asScala
}

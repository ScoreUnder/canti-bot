package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.JDA

import scala.collection.JavaConverters._

class RichJDA(val jDA: JDA) extends AnyVal {
  def guilds = jDA.getGuilds.asScala
}

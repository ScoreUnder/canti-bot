package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class RichJDA(val jda: JDA) extends AnyVal {
  def guilds: mutable.Buffer[Guild] = jda.getGuilds.asScala
}

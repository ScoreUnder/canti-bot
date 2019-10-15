package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Guild, User}

import scala.collection.JavaConverters._

class RichJDA(val jda: JDA) extends AnyVal {
  def guilds = jda.getGuilds.asScala
  def findGuild(guild: ID[Guild]) = Option(jda.getGuildById(guild.value))
  def findUser(user: ID[User]) = Option(jda.getUserById(user.value))
}

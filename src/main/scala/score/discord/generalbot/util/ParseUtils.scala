package score.discord.generalbot.util

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.{Guild, Role}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.util.Try
import scala.collection.JavaConverters._

object ParseUtils {
  def searchRoles(guild: Guild, roleName: String): Seq[Role] =
      Try(roleName.toLong)
        .map(id => List(guild.getRoleById(id)))
        .getOrElse(guild.getRolesByName(roleName, true).asScala)

  def findRole(guild: Guild, roleName: String): Either[EmbedBuilder, Role] =
    searchRoles(guild, roleName) match {
      case Nil =>
        Left(BotMessages.error("Could not find a role by that name.").
          addField("Search term", roleName, true))

      case Seq(role) =>
        Right(role)

      case matchingRoles @ Seq(_*) =>
        val embed = BotMessages.error("Too many roles by that name.").
          addField("Search term", roleName, true)

        for (role <- matchingRoles) {
          embed.appendDescription(s"\n`${role.id}`: ${role.mention}")
        }

        Left(embed)
    }
}

package score.discord.generalbot.util

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.{Guild, Role}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.util.Try
import scala.jdk.CollectionConverters._

object ParseUtils {
  def searchRoles(guild: Guild, roleName: String): Seq[Role] =
    if (roleName.isEmpty)
      Nil
    else
      roleName.toLongOption
        .map(id => List(guild.getRoleById(id)))
        .getOrElse(guild.getRolesByName(roleName, true).asScala)
        .toSeq

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
          embed.appendDescription(s"\n`${role.rawId}`: ${role.mention}")
        }

        Left(embed)
    }
}

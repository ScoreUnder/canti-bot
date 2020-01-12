package score.discord.generalbot.util

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.{Guild, Role}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.util.Try
import scala.jdk.CollectionConverters._

object ParseUtils {
  /** Searches the given guild for a role by the given name/ID.
    * If the provided string is a valid Long, it will match by ID, otherwise
    * it will match by name.
    * Not case sensitive.
    *
    * @param guild guild to search
    * @param roleName exact name of role
    * @return all roles that match
    */
  def searchRoles(guild: Guild, roleName: String): Seq[Role] =
    if (roleName.isEmpty)
      Nil
    else
      roleName.toLongOption
        .map(id => List(guild.getRoleById(id)))
        .getOrElse(guild.getRolesByName(roleName, true).asScala)
        .toSeq

  /** Searches the given guild for a single role by the given name/ID.
    * If there are multiple matches or no matches, a human-readable error
    * message will be returned in a Left.
    * Not case sensitive.
    *
    * @param guild guild to search
    * @param roleName exact name of role
    * @return either a human readable error, or the desired role
    */
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

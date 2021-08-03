package score.discord.canti.discord.permissions

import net.dv8tion.jda.api.entities.{PermissionOverride, Role, User}
import score.discord.canti.wrappers.jda.ID

sealed trait PermissionHolder:
  def asMention: String

final case class Role(id: ID[Role]) extends PermissionHolder:
  def asMention = s"<@&${id.value}>"
final case class Member(id: ID[User]) extends PermissionHolder:
  def asMention = s"<@${id.value}>"
final case class Unknown(id: ID[?]) extends PermissionHolder:
  def asMention = toString

object PermissionHolder:
  def apply(permissionOverride: PermissionOverride): PermissionHolder =
    if permissionOverride.isMemberOverride then Member(ID(permissionOverride.getIdLong))
    else if permissionOverride.isRoleOverride then Role(ID(permissionOverride.getIdLong))
    else Unknown(ID(permissionOverride.getIdLong))

package score.discord.canti.discord.permissions

import net.dv8tion.jda.api.entities.{Member as JDAMember, PermissionOverride, Role as JDARole, User}
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichSnowflake.*

sealed trait PermissionHolder:
  def asMention: String

object PermissionHolder:
  def apply(permissionOverride: PermissionOverride): PermissionHolder =
    if permissionOverride.isMemberOverride then Member(ID(permissionOverride.getIdLong))
    else if permissionOverride.isRoleOverride then Role(ID(permissionOverride.getIdLong))
    else Unknown(ID(permissionOverride.getIdLong))

  final case class Role(id: ID[JDARole]) extends PermissionHolder:
    def asMention = s"<@&${id.value}>"
  final case class Member(id: ID[JDAMember]) extends PermissionHolder:
    def asMention = s"<@${id.value}>"
  final case class Unknown(id: ID[?]) extends PermissionHolder:
    def asMention = toString

  extension (r: JDARole) inline def asPermissionHolder = Role(r.id)
  extension (m: JDAMember) inline def asPermissionHolder = Member(m.id)

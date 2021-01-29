package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{Member, Role}
import net.dv8tion.jda.internal.entities.MemberImpl

class RichMember(val member: Member) extends AnyVal {
  /** Check if this member has the given role.
    *
    * @param role role to check
    * @return true iff they have the role
    */
  def has(role: Role): Boolean = (member match {
    case x: MemberImpl => x.getRoleSet
    case x => x.getRoles
  }) contains role

  /** Used to add/remove roles from this member */
  def roles = new MemberRolesShim(member)
}

class MemberRolesShim(val member: Member) extends AnyVal {
  /** Add a role to this member.
    *
    * {{{ member.roles += someRole -> "audit log reason here" }}}
    *
    * @param roleReason role and reason for role change
    */
  def +=(roleReason: (Role, String)): Unit = member.getGuild.addRoleToMember(member, roleReason._1).reason(roleReason._2).queue()

  /** Remove a role from this member.
    *
    * {{{ member.roles -= someRole -> "audit log reason here" }}}
    *
    * @param roleReason role and reason for role change
    */
  def -=(roleReason: (Role, String)): Unit = member.getGuild.removeRoleFromMember(member, roleReason._1).reason(roleReason._2).queue()
}

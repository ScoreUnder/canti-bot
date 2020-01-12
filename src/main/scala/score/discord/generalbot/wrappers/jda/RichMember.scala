package score.discord.generalbot.wrappers.jda

import java.util

import net.dv8tion.jda.api.entities.{Member, Role}
import net.dv8tion.jda.internal.entities.MemberImpl

class RichMember(val member: Member) extends AnyVal {
  /** Check if this member has the given role.
    *
    * @param role role to check
    * @return true iff they have the role
    */
  def has(role: Role) = (member match {
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
  def +=(roleReason: (Role, String)) = member.getGuild.modifyMemberRoles(member, util.Arrays.asList(roleReason._1), null).reason(roleReason._2).queue()

  /** Remove a role from this member.
    *
    * {{{ member.roles -= someRole -> "audit log reason here" }}}
    *
    * @param roleReason role and reason for role change
    */
  def -=(roleReason: (Role, String)) = member.getGuild.modifyMemberRoles(member, null, util.Arrays.asList(roleReason._1)).reason(roleReason._2).queue()
}

package score.discord.generalbot.wrappers.jda

import java.util

import net.dv8tion.jda.api.entities.{Member, Role}
import net.dv8tion.jda.internal.entities.MemberImpl

class RichMember(val member: Member) extends AnyVal {
  def has(role: Role) = (member match {
    case x: MemberImpl => x.getRoleSet
    case x => x.getRoles
  }) contains role

  def roles = new MemberRolesShim(member)
}

class MemberRolesShim(val member: Member) extends AnyVal {
  def +=(roleReason: (Role, String)) = member.getGuild.modifyMemberRoles(member, util.Arrays.asList(roleReason._1), null).reason(roleReason._2).queue()

  def -=(roleReason: (Role, String)) = member.getGuild.modifyMemberRoles(member, null, util.Arrays.asList(roleReason._1)).reason(roleReason._2).queue()
}

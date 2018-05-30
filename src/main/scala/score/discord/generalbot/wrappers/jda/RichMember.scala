package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.impl.MemberImpl
import net.dv8tion.jda.core.entities.{Member, Role}

class RichMember(val member: Member) extends AnyVal {
  def has(role: Role) = (member match {
    case x: MemberImpl => x.getRoleSet
    case x => x.getRoles
  }) contains role

  def roles = new MemberRolesShim(member)
}

class MemberRolesShim(val member: Member) extends AnyVal {
  def +=(roleReason: (Role, String)) = member.getGuild.getController.addRolesToMember(member, roleReason._1).reason(roleReason._2).queue()

  def -=(roleReason: (Role, String)) = member.getGuild.getController.removeRolesFromMember(member, roleReason._1).reason(roleReason._2).queue()
}

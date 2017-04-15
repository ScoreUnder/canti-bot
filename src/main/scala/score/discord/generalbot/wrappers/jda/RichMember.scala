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
  def +=(role: Role) = member.getGuild.getController.addRolesToMember(member, role).queue()

  def -=(role: Role) = member.getGuild.getController.removeRolesFromMember(member, role).queue()
}

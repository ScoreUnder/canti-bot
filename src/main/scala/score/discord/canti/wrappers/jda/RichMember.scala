package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{Member, Role}
import net.dv8tion.jda.internal.entities.MemberImpl
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichUser.unambiguousString as userUnambiguousString

import scala.concurrent.Future

object RichMember:
  extension (member: Member)

    /** Check if this member has the given role.
      *
      * @param role
      *   role to check
      * @return
      *   true iff they have the role
      */
    infix def has(role: Role): Boolean = (member match
      case x: MemberImpl => x.getRoleSet
      case x             => x.getRoles
    ).nn.contains(role)

    /** Used to add/remove roles from this member */
    inline def roles = MemberRolesShim(member)

    def unambiguousString = s"${member.getUser.nn.userUnambiguousString} @ ${member.getGuild}"

  class MemberRolesShim(val member: Member) extends AnyVal:
    /** Add a role to this member.
      *
      * {{{member.roles += someRole -> "audit log reason here"}}}
      *
      * @param roleReason
      *   role and reason for role change
      */
    def +=(roleReason: (Role, String)): Future[Void] =
      val (role, reason) = roleReason
      member.getGuild.nn
        .addRoleToMember(member, role).nn
        .reason(reason).nn
        .addCheck { () =>
          !(member has role)
        }.nn
        .queueFuture()

    /** Remove a role from this member.
      *
      * {{{member.roles -= someRole -> "audit log reason here"}}}
      *
      * @param roleReason
      *   role and reason for role change
      */
    def -=(roleReason: (Role, String)): Future[Void] =
      val (role, reason) = roleReason
      member.getGuild.nn
        .removeRoleFromMember(member, role).nn
        .reason(reason).nn
        .addCheck { () =>
          member has role
        }.nn
        .queueFuture()
end RichMember

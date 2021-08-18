package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{Member, Role}
import net.dv8tion.jda.internal.entities.MemberImpl
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichUser.{unambiguousString as userUnambiguousString}

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
      case x: MemberImpl => x.getRoleSet.nn
      case x             => x.getRoles
    ).contains(role)

    /** Used to add/remove roles from this member */
    inline def roles = MemberRolesShim(member)

    def unambiguousString = s"${member.getUser.userUnambiguousString} @ ${member.getGuild}"

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
      member.getGuild
        .addRoleToMember(member, role)
        .reason(reason)
        .addCheck { () =>
          !(member has role)
        }
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
      member.getGuild
        .removeRoleFromMember(member, role)
        .reason(reason)
        .addCheck { () =>
          member has role
        }
        .queueFuture()
end RichMember

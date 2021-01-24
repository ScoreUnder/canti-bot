package score.discord.generalbot.discord.permissions

import net.dv8tion.jda.api.Permission

case class PermissionAttachment(allows: Set[Permission] = Set.empty, denies: Set[Permission] = Set.empty) {
  def allow(perms: Permission*): PermissionAttachment =
    copy(allows = allows | perms.toSet, denies = denies &~ perms.toSet)

  def deny(perms: Permission*): PermissionAttachment =
    copy(allows = allows &~ perms.toSet, denies = denies | perms.toSet)

  def clear(perms: Permission*): PermissionAttachment =
    copy(allows = allows &~ perms.toSet, denies = denies &~ perms.toSet)

  def merge(other: PermissionAttachment): PermissionAttachment =
    copy(allows = allows &~ other.denies | other.allows, denies = denies &~ other.allows | other.denies)
}

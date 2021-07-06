package score.discord.canti.discord.permissions

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.PermissionOverride
import scala.jdk.CollectionConverters.*

case class PermissionAttachment(
  allows: Set[Permission] = Set.empty,
  denies: Set[Permission] = Set.empty
):
  def allow(perms: Permission*): PermissionAttachment =
    copy(allows = allows | perms.toSet, denies = denies &~ perms.toSet)

  def deny(perms: Permission*): PermissionAttachment =
    copy(allows = allows &~ perms.toSet, denies = denies | perms.toSet)

  def clear(perms: Permission*): PermissionAttachment =
    copy(allows = allows &~ perms.toSet, denies = denies &~ perms.toSet)

  def merge(other: PermissionAttachment): PermissionAttachment =
    copy(
      allows = allows &~ other.denies | other.allows,
      denies = denies &~ other.allows | other.denies
    )

  def isEmpty: Boolean = allows.isEmpty && denies.isEmpty

object PermissionAttachment:
  def apply(ov: PermissionOverride): PermissionAttachment =
    PermissionAttachment(allows = ov.getAllowed.asScala.toSet, denies = ov.getDenied.asScala.toSet)

  def empty: PermissionAttachment = PermissionAttachment(Set.empty, Set.empty)

package score.discord.canti.discord.permissions

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.PermissionOverride
import scala.jdk.CollectionConverters.*

import PermissionValue.*

case class PermissionAttachment(permissions: Map[Permission, PermissionValue]):
  def allow(perms: Iterable[Permission]): PermissionAttachment =
    copy(permissions = permissions ++ perms.map(_ -> Allow))

  def deny(perms: Iterable[Permission]): PermissionAttachment =
    copy(permissions = permissions ++ perms.map(_ -> Deny))

  def clear(perms: Iterable[Permission]): PermissionAttachment =
    copy(permissions = permissions -- perms)

  def merge(other: PermissionAttachment): PermissionAttachment =
    copy(permissions = permissions ++ other.permissions.filter { case (_, v) =>
      v != Inherit
    })

  def allows: Set[Permission] =
    permissions.view.collect { case (k, Allow) => k }.toSet

  def denies: Set[Permission] =
    permissions.view.collect { case (k, Deny) => k }.toSet

  def get(perm: Permission): PermissionValue =
    permissions.getOrElse(perm, Inherit)

  export permissions.isEmpty

object PermissionAttachment:
  def apply(ov: PermissionOverride): PermissionAttachment =
    empty.allow(ov.getAllowed.asScala).deny(ov.getDenied.asScala)

  val empty: PermissionAttachment =
    PermissionAttachment(Map.empty.withDefaultValue(Inherit))

  export empty.{allow, deny}

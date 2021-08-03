package score.discord.canti.discord.permissions

import net.dv8tion.jda.api.entities.IPermissionHolder
import scala.annotation.targetName

case class PermissionCollection[+T <: IPermissionHolder](values: Seq[(T, PermissionAttachment)]):
  def :+[U >: T <: IPermissionHolder](value: (U, PermissionAttachment)): PermissionCollection[U] =
    copy(values = values :+ value)

  def merge[U >: T <: IPermissionHolder](other: PermissionCollection[U]): PermissionCollection[U] =
    if values.isEmpty then other
    else if other.values.isEmpty then this
    else
      val otherMap = other.values.toMap
      val ourMap = values.toMap[U, PermissionAttachment]
      val othersOnly = otherMap.keySet &~ ourMap.keySet

      val oursMerged = ourMap.transform { case (k, v) =>
        otherMap.get(k).fold(v)(v.merge)
      }
      val remains = othersOnly.map(v => v -> otherMap(v)).toMap
      PermissionCollection((oursMerged ++ remains).toSeq)

  def mapValues(f: PermissionAttachment => PermissionAttachment): PermissionCollection[T] =
    PermissionCollection(values.map { case (k, v) => k -> f(v) })

object PermissionCollection:
  @targetName("apply_varargs")
  def apply[T <: IPermissionHolder](values: (T, PermissionAttachment)*): PermissionCollection[T] =
    PermissionCollection(values)

  val empty: PermissionCollection[Nothing] = PermissionCollection(Seq.empty)

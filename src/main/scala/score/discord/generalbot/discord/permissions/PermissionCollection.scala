package score.discord.generalbot.discord.permissions

import net.dv8tion.jda.api.entities.IPermissionHolder

case class PermissionCollection[+T <: IPermissionHolder] private(values: Seq[(T, PermissionAttachment)]) {
  def :+[U >: T <: IPermissionHolder](value: (U, PermissionAttachment)): PermissionCollection[U] = copy(values = values :+ value)

  def merge[U >: T <: IPermissionHolder](other: PermissionCollection[U]): PermissionCollection[U] = {
    if (values.isEmpty) {
      other
    } else if (other.values.isEmpty) {
      this
    } else {
      val otherMap = other.values.toMap
      val ourMap = values.toMap[U, PermissionAttachment]
      val othersOnly = otherMap.keySet &~ ourMap.keySet

      val transformed = ourMap.transform {
        case (k, v) => otherMap.get(k).fold(v)(v.merge)
      }
      val remains = othersOnly.map(v => v -> otherMap(v)).toMap
      PermissionCollection((transformed ++ remains).toSeq)
    }
  }

  def mapValues(f: PermissionAttachment => PermissionAttachment): PermissionCollection[T] =
    PermissionCollection(values.map { case (k, v) => k -> f(v) })
}

object PermissionCollection {
  def empty[T <: IPermissionHolder]: PermissionCollection[T] = PermissionCollection(Seq.empty)
}

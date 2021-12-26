package score.discord.canti.wrappers.database

import score.discord.canti.wrappers.jda.ID
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}

object IDMapping {
  implicit def idMapping[T](implicit
    profile: JdbcProfile
  ): JdbcType[ID[T]] with BaseTypedType[ID[T]] = {
    import profile.api._
    MappedColumnType.base[ID[T], Long]({ id => id.value }, { long => new ID[T](long) })
  }
}

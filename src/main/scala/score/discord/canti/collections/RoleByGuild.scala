package score.discord.canti.collections

import net.dv8tion.jda.api.entities.{Guild, Role}
import score.discord.canti.util.DBUtils
import score.discord.canti.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RoleByGuild(dbConfig: DatabaseConfig[_ <: JdbcProfile],
  tableName: String) extends AsyncMap[ID[Guild], ID[Role]] {

  import dbConfig.profile.api._

  private class RoleByGuild(tag: Tag, name: String) extends Table[(ID[Guild], ID[Role])](tag, name) {
    val guildId = column[ID[Guild]]("guild", O.PrimaryKey)
    val roleId = column[ID[Role]]("role")

    override def * = (guildId, roleId)
  }

  private val database = dbConfig.db
  private val roleByGuildTable = TableQuery[RoleByGuild](new RoleByGuild(_: Tag, tableName))
  private val lookupQuery = Compiled((guildId: ConstColumn[ID[Guild]]) => {
    roleByGuildTable.filter(t => t.guildId === guildId).map(_.roleId)
  })
  DBUtils.ensureTableCreated(dbConfig, roleByGuildTable, tableName)

  override def get(key: ID[Guild]): Future[Option[ID[Role]]] =
    dbConfig.db.run(lookupQuery(key).result).map(_.headOption)

  override def update(guild: ID[Guild], role: ID[Role]): Future[Int] =
    database.run(roleByGuildTable.insertOrUpdate(guild, role))

  override def remove(guild: ID[Guild]): Future[Int] =
    database.run(lookupQuery(guild).delete)

  override def items: Future[Seq[(ID[Guild], ID[Role])]] = throw new UnsupportedOperationException()
}

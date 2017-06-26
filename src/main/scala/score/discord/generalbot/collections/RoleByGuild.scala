package score.discord.generalbot.collections

import net.dv8tion.jda.core.entities.{Guild, Role}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object RoleByGuild {
  private[RoleByGuild] type MyCache = Cache[Guild, ID[Guild], Option[ID[Role]]]
}

import score.discord.generalbot.collections.RoleByGuild.MyCache

class RoleByGuild(dbConfig: DatabaseConfig[_ <: JdbcProfile],
                  cacheFactory: (MyCache#Backend) => MyCache,
                  tableName: String) {

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

  private val cache = cacheFactory(new MyCache#Backend {
    override def keyToId(key: Guild): ID[Guild] = key.id

    override def get(key: Guild): Future[Option[ID[Role]]] =
      dbConfig.db.run(lookupQuery(key.id).result).map(_.headOption)
  })

  Await.result(database.run(MTable.getTables).map(v => {
    val names = v.map(mt => mt.name.name)
    if (!names.contains(tableName)) {
      Await.result(database.run(roleByGuildTable.schema.create), Duration.Inf)
    }
  }), Duration.Inf)

  def apply(guild: Guild): Future[Option[Role]] = cache(guild).map(_.flatMap(guild.findRole))

  def update(guild: Guild, role: Role) {
    cache(guild) = Some(role.id)
    // TODO: Do I need to await this?
    database.run(roleByGuildTable.insertOrUpdate(guild.id, role.id))
  }

  def remove(guild: Guild): Unit = remove(guild.id)

  def remove(guild: ID[Guild]) {
    cache.updateById(guild, None)
    // TODO: Do I need to await this?
    database.run(lookupQuery(guild).delete)
  }
}

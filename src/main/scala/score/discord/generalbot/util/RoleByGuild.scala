package score.discord.generalbot.util

import net.dv8tion.jda.core.entities.{Guild, Role}
import score.discord.generalbot.wrappers.jda.Conversions._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class RoleByGuild(dbConfig: DatabaseConfig[_ <: JdbcProfile], tableName: String) {
  import dbConfig.profile.api._
  private class RoleByGuild(tag: Tag, name: String) extends Table[(Long, Long)](tag, name) {
    val guildId = column[Long]("guild", O.PrimaryKey)
    val roleId = column[Long]("role")

    override def * = (guildId, roleId)
  }
  private val database = dbConfig.db
  private val roleByGuildTable = TableQuery[RoleByGuild](new RoleByGuild(_: Tag, tableName))

  Await.result(database.run(MTable.getTables).map(v => {
    val names = v.map(mt => mt.name.name)
    if (!names.contains(tableName)) {
      Await.result(database.run(roleByGuildTable.schema.create), Duration.Inf)
    }
  }), Duration.Inf)

  private val roleByGuild = mutable.HashMap(
    Await.result(database.run(roleByGuildTable.result), Duration.Inf): _*
  )

  def apply(guild: Guild): Option[Role] = apply(guild.id).flatMap(roleId => Option(guild.getRoleById(roleId)))

  def apply(guild: Long): Option[Long] = roleByGuild.get(guild)

  def update(guild: Guild, role: Role): Unit = update(guild.id, role.id)

  def update(guild: Long, role: Long) {
    roleByGuild(guild) = role
    // TODO: Do I need to await this?
    database.run(roleByGuildTable.insertOrUpdate(guild, role))
  }

  def remove(guild: Guild): Unit = remove(guild.id)

  def remove(guild: Long) {
    roleByGuild.remove(guild)
    // TODO: Do I need to await this?
    database.run(roleByGuildTable.filter(_.guildId === guild).delete)
  }
}

package score.discord.generalbot.collections

import net.dv8tion.jda.core.entities.{Guild, Role}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class RoleByGuild(dbConfig: DatabaseConfig[_ <: JdbcProfile], tableName: String) {
  import dbConfig.profile.api._
  private class RoleByGuild(tag: Tag, name: String) extends Table[(ID[Guild], ID[Role])](tag, name) {
    val guildId = column[ID[Guild]]("guild", O.PrimaryKey)
    val roleId = column[ID[Role]]("role")

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

  def apply(guild: Guild): Option[Role] = apply(guild.typedId).flatMap(guild.findRole)

  def apply(guild: ID[Guild]): Option[ID[Role]] = roleByGuild.get(guild)

  def update(guild: Guild, role: Role): Unit = update(guild.typedId, role.typedId)

  def update(guild: ID[Guild], role: ID[Role]) {
    roleByGuild(guild) = role
    // TODO: Do I need to await this?
    database.run(roleByGuildTable.insertOrUpdate(guild, role))
  }

  def remove(guild: Guild): Unit = remove(guild.typedId)

  def remove(guild: ID[Guild]) {
    roleByGuild.remove(guild)
    // TODO: Do I need to await this?
    database.run(roleByGuildTable.filter(_.guildId === guild).delete)
  }
}

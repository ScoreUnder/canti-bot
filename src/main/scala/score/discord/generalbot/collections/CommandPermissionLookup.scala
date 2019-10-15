package score.discord.generalbot.collections

import net.dv8tion.jda.api.entities.{Guild, ISnowflake, Role}
import score.discord.generalbot.command.Command
import score.discord.generalbot.util.DBUtils
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CommandPermissionLookup(dbConfig: DatabaseConfig[_ <: JdbcProfile],
                              cacheBase: Cache[(ID[Command], ID[Guild]), Option[ID[Role]]],
                              tableName: String) {

  import dbConfig.profile.api._

  private class CommandPermissionTable(tag: Tag) extends Table[(ID[Command], ID[Guild], ID[Role])](tag, tableName) {
    val commandId = column[ID[Command]]("command")
    val guildId = column[ID[Guild]]("guild")
    val roleId = column[ID[Role]]("role")
    val pk = primaryKey("primary", (commandId, guildId))

    override def * = (commandId, guildId, roleId)
  }

  private val commandPermissionTable = TableQuery[CommandPermissionTable]
  private[this] val lookupQuery = Compiled((commandId: ConstColumn[ID[Command]], guildId: ConstColumn[ID[Guild]]) =>
    commandPermissionTable.filter(t => t.commandId === commandId && t.guildId === guildId).map(_.roleId)
  )
  private[this] val cache = new cacheBase.Backend[(Command with ISnowflake, Guild)] {
    override def keyToId(key: (Command with ISnowflake, Guild)): (ID[Command], ID[Guild]) = (key._1.id, key._2.id)

    override def missing(key: (Command with ISnowflake, Guild)): Future[Option[ID[Role]]] =
      dbConfig.db.run(lookupQuery(key._1.id, key._2.id).result).map(_.headOption)
  }

  DBUtils.ensureTableCreated(dbConfig, commandPermissionTable, tableName)

  def apply(command: Command with ISnowflake, guild: Guild, default: Option[Role] = None): Future[Option[Role]] =
    cache((command, guild)).map(_ map guild.findRole getOrElse default)

  def update(command: Command with ISnowflake, guild: Guild, role: Role): Future[Int] = {
    cache((command, guild)) = Some(role.id)
    dbConfig.db.run(commandPermissionTable.insertOrUpdate(command.id, guild.id, role.id))
  }

  def remove(command: Command with ISnowflake, guild: Guild): Future[Int] = {
    cache((command, guild)) = None
    dbConfig.db.run(lookupQuery(command.id, guild.id).delete)
  }
}

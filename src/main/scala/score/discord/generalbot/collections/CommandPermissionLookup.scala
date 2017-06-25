package score.discord.generalbot.collections

import net.dv8tion.jda.core.entities.{Guild, ISnowflake, Role}
import score.discord.generalbot.command.Command
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class CommandPermissionLookup(databaseConfig: DatabaseConfig[_ <: JdbcProfile], tableName: String) {

  import databaseConfig.profile.api._
  private class CommandPermissionTable(tag: Tag) extends Table[(ID[Command], ID[Guild], ID[Role])](tag, tableName) {
    val commandId = column[ID[Command]]("command")
    val guildId = column[ID[Guild]]("guild")
    val roleId = column[ID[Role]]("role")
    val pk = primaryKey("primary", (commandId, guildId))

    override def * = (commandId, guildId, roleId)
  }

  private val commandPermissionTable = TableQuery[CommandPermissionTable]

  Await.result(databaseConfig.db.run(MTable.getTables).map(v => {
    val names = v.map(mt => mt.name.name)
    if (!names.contains(tableName)) {
      Await.result(databaseConfig.db.run(commandPermissionTable.schema.create), Duration.Inf)
    }
  }), Duration.Inf)

  private val commandPermissionLookup = mutable.HashMap(
    Await.result(databaseConfig.db.run(commandPermissionTable.result), Duration.Inf).map({
      case (command, guild, role) => (command, guild) -> role
    }): _*
  )

  def apply(command: Command with ISnowflake, guild: Guild, default: Option[Role] = None): Option[Role] =
    apply(command.id, guild.id) map guild.findRole getOrElse default

  def apply(command: ID[Command], guild: ID[Guild]) = commandPermissionLookup.get((command, guild))

  def update(command: Command with ISnowflake, guild: Guild, role: Role): Unit =
    update(command.id, guild.id, role.id)

  def update(command: ID[Command], guild: ID[Guild], role: ID[Role]) {
    commandPermissionLookup((command, guild)) = role
    // TODO: Do I need to await this?
    databaseConfig.db.run(commandPermissionTable.insertOrUpdate(command, guild, role))
  }

  def remove(command: Command with ISnowflake, guild: Guild): Unit = remove(command.id, guild.id)

  def remove(command: ID[Command], guild: ID[Guild]) {
    commandPermissionLookup.remove((command, guild))
    // TODO: Do I need to await this?
    databaseConfig.db.run(commandPermissionTable.filter(t => t.commandId === command && t.guildId === guild).delete)
  }
}

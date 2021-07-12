package score.discord.canti.collections

import net.dv8tion.jda.api.entities.{Guild, GuildChannel as Channel}
import score.discord.canti.util.DBUtils
import score.discord.canti.wrappers.database.IDMapping.*
import score.discord.canti.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ChannelByGuild(dbConfig: DatabaseConfig[? <: JdbcProfile], tableName: String)
    extends AsyncMap[ID[Guild], ID[Channel]]:

  import dbConfig.profile.api.*

  private class ChannelByGuild(tag: Tag, name: String)
      extends Table[(ID[Guild], ID[Channel])](tag, name):
    val guildId = column[ID[Guild]]("guild", O.PrimaryKey)
    val channelId = column[ID[Channel]]("channel")

    override def * = (guildId, channelId)

  private val database = dbConfig.db
  private val channelByGuildTable =
    TableQuery[ChannelByGuild](new ChannelByGuild(_: Tag, tableName))
  private val lookupQuery = Compiled(
  (guildId: ConstColumn[ID[Guild]]) =>
    channelByGuildTable.filter(t => t.guildId === guildId).map(_.channelId))
  DBUtils.ensureTableCreated(dbConfig, channelByGuildTable, tableName)

  override def get(key: ID[Guild]): Future[Option[ID[Channel]]] =
    dbConfig.db.run(lookupQuery(key).result).map(_.headOption)

  override def update(guild: ID[Guild], role: ID[Channel]): Future[Int] =
    database.run(channelByGuildTable.insertOrUpdate(guild, role))

  override def remove(guild: ID[Guild]): Future[Int] =
    database.run(lookupQuery(guild).delete)

  override def items: Future[Seq[(ID[Guild], ID[Channel])]] =
    throw new UnsupportedOperationException()

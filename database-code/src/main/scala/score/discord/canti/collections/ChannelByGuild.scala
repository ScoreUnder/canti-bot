package score.discord.canti.collections

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import score.discord.canti.util.DBUtils
import score.discord.canti.wrappers.database.IDMapping._
import score.discord.canti.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ChannelByGuild(dbConfig: DatabaseConfig[_ <: JdbcProfile], tableName: String)
    extends AsyncMap[ID[Guild], ID[GuildChannel]] {

  import dbConfig.profile.api._

  private class ChannelByGuild(tag: Tag, name: String)
      extends Table[(ID[Guild], ID[GuildChannel])](tag, name) {
    val guildId = column[ID[Guild]]("guild", O.PrimaryKey)
    val channelId = column[ID[GuildChannel]]("channel")

    override def * = (guildId, channelId)
  }

  private val database = dbConfig.db
  private val channelByGuildTable =
    TableQuery[ChannelByGuild](new ChannelByGuild(_: Tag, tableName))
  private val lookupQuery = Compiled((guildId: ConstColumn[ID[Guild]]) => {
    channelByGuildTable.filter(t => t.guildId === guildId).map(_.channelId)
  })
  DBUtils.ensureTableCreated(dbConfig, channelByGuildTable, tableName)

  override def get(key: ID[Guild]): Future[Option[ID[GuildChannel]]] =
    dbConfig.db.run(lookupQuery(key).result).map(_.headOption)

  override def update(guild: ID[Guild], role: ID[GuildChannel]): Future[Int] =
    database.run(channelByGuildTable.insertOrUpdate(guild, role))

  override def remove(guild: ID[Guild]): Future[Int] =
    database.run(lookupQuery(guild).delete)

  override def items: Future[Seq[(ID[Guild], ID[GuildChannel])]] =
    throw new UnsupportedOperationException()
}

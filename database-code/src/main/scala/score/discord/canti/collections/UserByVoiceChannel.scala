package score.discord.canti.collections

import net.dv8tion.jda.api.entities.*
import score.discord.canti.util.DBUtils
import score.discord.canti.wrappers.database.IDMapping.*
import score.discord.canti.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserByVoiceChannel(dbConfig: DatabaseConfig[? <: JdbcProfile], tableName: String)
    extends AsyncMap[(ID[Guild], ID[VoiceChannel]), ID[User]]:

  import dbConfig.profile.api.*

  private class UserByChannelTable(tag: Tag)
      extends Table[(ID[Guild], ID[VoiceChannel], ID[User])](tag, tableName):
    val guildId = column[ID[Guild]]("guild")
    val channelId = column[ID[VoiceChannel]]("channel")
    val userId = column[ID[User]]("user")
    val pk = primaryKey("primary", (guildId, channelId))

    override def * = (guildId, channelId, userId)

  private val userByChannelTable = TableQuery[UserByChannelTable]
  private val lookupQuery = Compiled(
  (guildId: ConstColumn[ID[Guild]], channelId: ConstColumn[ID[VoiceChannel]]) =>
    userByChannelTable
      .filter(t => t.guildId === guildId && t.channelId === channelId)
      .map(_.userId))

  DBUtils.ensureTableCreated(dbConfig, userByChannelTable, tableName)

  override def get(key: (ID[Guild], ID[VoiceChannel])): Future[Option[ID[User]]] =
    val (guild, channel) = key
    dbConfig.db.run(lookupQuery(guild, channel).result).map(_.headOption)

  override def update(key: (ID[Guild], ID[VoiceChannel]), value: ID[User]): Future[Int] =
    val (guild, channel) = key
    dbConfig.db.run(userByChannelTable.insertOrUpdate(guild, channel, value))

  // Duplicated here so as not to pull "conversions" object any higher
  private def idHack[T <: ISnowflake](s: T) = new ID[T](s.getIdLong)

  def remove(channel: VoiceChannel): Future[Int] =
    dbConfig.db.run(lookupQuery(idHack(channel.getGuild), idHack(channel)).delete)

  override def remove(key: (ID[Guild], ID[VoiceChannel])): Future[Int] =
    val (guild, channel) = key
    dbConfig.db.run(lookupQuery(guild, channel).delete)

  override def items: Future[Seq[((ID[Guild], ID[VoiceChannel]), ID[User])]] =
    dbConfig.db.run(userByChannelTable.result).map(_.map(t => ((t._1, t._2), t._3)))

package score.discord.canti.collections

import net.dv8tion.jda.api.entities.{Guild, User, ISnowflake}
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import score.discord.canti.util.DBUtils
import score.discord.canti.wrappers.database.IDMapping._
import score.discord.canti.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserByAudioChannel(dbConfig: DatabaseConfig[_ <: JdbcProfile], tableName: String)
    extends AsyncMap[(ID[Guild], ID[AudioChannel]), ID[User]] {

  import dbConfig.profile.api._

  private class UserByChannelTable(tag: Tag)
      extends Table[(ID[Guild], ID[AudioChannel], ID[User])](tag, tableName) {
    val guildId = column[ID[Guild]]("guild")
    val channelId = column[ID[AudioChannel]]("channel")
    val userId = column[ID[User]]("user")
    val pk = primaryKey("primary", (guildId, channelId))

    override def * = (guildId, channelId, userId)
  }

  private val userByChannelTable = TableQuery[UserByChannelTable]
  private val lookupQuery =
    Compiled((guildId: ConstColumn[ID[Guild]], channelId: ConstColumn[ID[AudioChannel]]) => {
      userByChannelTable
        .filter(t => t.guildId === guildId && t.channelId === channelId)
        .map(_.userId)
    })

  DBUtils.ensureTableCreated(dbConfig, userByChannelTable, tableName)

  override def get(key: (ID[Guild], ID[AudioChannel])): Future[Option[ID[User]]] = {
    val (guild, channel) = key
    dbConfig.db.run(lookupQuery(guild, channel).result).map(_.headOption)
  }

  override def update(key: (ID[Guild], ID[AudioChannel]), value: ID[User]): Future[Int] = {
    val (guild, channel) = key
    dbConfig.db.run(userByChannelTable.insertOrUpdate(guild, channel, value))
  }

  // Duplicated here so as not to pull "conversions" object any higher
  private def idHack[T <: ISnowflake](s: T) = new ID[T](s.getIdLong)

  def remove(channel: AudioChannel): Future[Int] =
    dbConfig.db.run(lookupQuery(idHack(channel.getGuild), idHack(channel)).delete)

  override def remove(key: (ID[Guild], ID[AudioChannel])): Future[Int] = {
    val (guild, channel) = key
    dbConfig.db.run(lookupQuery(guild, channel).delete)
  }

  override def items: Future[Seq[((ID[Guild], ID[AudioChannel]), ID[User])]] =
    dbConfig.db.run(userByChannelTable.result).map(_.map(t => ((t._1, t._2), t._3)))
}

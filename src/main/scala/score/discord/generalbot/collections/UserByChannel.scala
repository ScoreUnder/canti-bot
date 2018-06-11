package score.discord.generalbot.collections

import net.dv8tion.jda.core.entities._
import score.discord.generalbot.util.DBUtils
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserByChannel(dbConfig: DatabaseConfig[_ <: JdbcProfile],
                    cacheBase: Cache[ID[Channel], Option[ID[User]]],
                    tableName: String) {

  import dbConfig.profile.api._

  private class UserByChannelTable(tag: Tag) extends Table[(ID[Guild], ID[Channel], ID[User])](tag, tableName) {
    val guildId = column[ID[Guild]]("guild")
    val channelId = column[ID[Channel]]("channel")
    val userId = column[ID[User]]("user")
    val pk = primaryKey("primary", (guildId, channelId))

    override def * = (guildId, channelId, userId)
  }

  private val userByChannelTable = TableQuery[UserByChannelTable]
  private val lookupQuery = Compiled((guildId: ConstColumn[ID[Guild]], channelId: ConstColumn[ID[Channel]]) => {
    userByChannelTable.filter(t => t.guildId === guildId && t.channelId === channelId).map(_.userId)
  })

  private val cache = new cacheBase.Backend[Channel] {
    override def keyToId(key: Channel): ID[Channel] = key.id

    override def missing(channel: Channel): Future[Option[ID[User]]] =
      dbConfig.db.run(lookupQuery(channel.getGuild.id, channel.id).result).map(_.headOption)
  }

  DBUtils.ensureTableCreated(dbConfig, userByChannelTable, tableName)

  def apply(channel: Channel): Future[Option[User]] = {
    val jda = channel.getJDA
    cache(channel).map(_.flatMap(jda.findUser))
  }

  def update(channel: Channel, user: User): Future[Int] = {
    cache(channel) = Some(user.id)
    dbConfig.db.run(userByChannelTable.insertOrUpdate(channel.getGuild.id, channel.id, user.id))
  }

  def remove(channel: Channel): Future[Int] = {
    cache(channel) = None
    dbConfig.db.run(lookupQuery(channel.getGuild.id, channel.id).delete)
  }

  def remove(guild: ID[Guild], channel: ID[Channel]): Future[Int] = {
    cache.updateById(channel, None)
    dbConfig.db.run(lookupQuery(guild, channel).delete)
  }

  def all: Future[Seq[(ID[Guild], ID[Channel], ID[User])]] = dbConfig.db.run(userByChannelTable.result)
}

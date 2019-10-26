package score.discord.generalbot.collections

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities._
import score.discord.generalbot.util.DBUtils
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.IdConversions._
import score.discord.generalbot.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserByVoiceChannel(dbConfig: DatabaseConfig[_ <: JdbcProfile],
                         cacheBase: Cache[ID[VoiceChannel], Option[ID[User]]],
                         tableName: String) {

  import dbConfig.profile.api._

  private class UserByChannelTable(tag: Tag) extends Table[(ID[Guild], ID[VoiceChannel], ID[User])](tag, tableName) {
    val guildId = column[ID[Guild]]("guild")
    val channelId = column[ID[VoiceChannel]]("channel")
    val userId = column[ID[User]]("user")
    val pk = primaryKey("primary", (guildId, channelId))

    override def * = (guildId, channelId, userId)
  }

  private val userByChannelTable = TableQuery[UserByChannelTable]
  private val lookupQuery = Compiled((guildId: ConstColumn[ID[Guild]], channelId: ConstColumn[ID[VoiceChannel]]) => {
    userByChannelTable.filter(t => t.guildId === guildId && t.channelId === channelId).map(_.userId)
  })

  private val cache = new cacheBase.Backend[VoiceChannel] {
    override def keyToId(key: VoiceChannel): ID[VoiceChannel] = key.id

    override def missing(channel: VoiceChannel): Future[Option[ID[User]]] =
      dbConfig.db.run(lookupQuery(channel.getGuild.id, channel.id).result).map(_.headOption)
  }

  DBUtils.ensureTableCreated(dbConfig, userByChannelTable, tableName)

  def apply(channel: VoiceChannel): Future[Option[User]] = {
    implicit val jda: JDA = channel.getJDA
    cache(channel).map(_.flatMap(_.find))
  }

  def update(channel: VoiceChannel, user: User): Future[Int] = {
    cache(channel) = Some(user.id)
    dbConfig.db.run(userByChannelTable.insertOrUpdate(channel.getGuild.id, channel.id, user.id))
  }

  def remove(channel: VoiceChannel): Future[Int] = {
    cache(channel) = None
    dbConfig.db.run(lookupQuery(channel.getGuild.id, channel.id).delete)
  }

  def remove(guild: ID[Guild], channel: ID[VoiceChannel]): Future[Int] = {
    cache.updateById(channel, None)
    dbConfig.db.run(lookupQuery(guild, channel).delete)
  }

  def all: Future[Seq[(ID[Guild], ID[VoiceChannel], ID[User])]] = dbConfig.db.run(userByChannelTable.result)
}

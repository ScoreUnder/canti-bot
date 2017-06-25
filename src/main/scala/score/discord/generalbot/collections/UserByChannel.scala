package score.discord.generalbot.collections

import net.dv8tion.jda.core.entities._
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object UserByChannel {
  private[UserByChannel] type MyCache = Cache[Channel, ID[Channel], Option[ID[User]]]
}

import score.discord.generalbot.collections.UserByChannel.MyCache

class UserByChannel(dbConfig: DatabaseConfig[_ <: JdbcProfile],
                    cacheFactory: (MyCache#Backend) => MyCache,
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

  private val cache = cacheFactory(new MyCache#Backend {
    override def keyToId(key: Channel): ID[Channel] = key.typedId

    override def get(channel: Channel): Future[Option[ID[User]]] =
      dbConfig.db.run(lookupQuery(channel.getGuild.typedId, channel.typedId).result).map(_.headOption)
  })

  // Ensure table exists on startup
  Await.result(dbConfig.db.run(MTable.getTables).map(v => {
    val names = v.map(mt => mt.name.name)
    if (!names.contains(tableName)) {
      Await.result(dbConfig.db.run(userByChannelTable.schema.create), Duration.Inf)
    }
  }), Duration.Inf)

  def apply(channel: Channel): Future[Option[User]] = {
    val jda = channel.getJDA
    cache(channel).map(_.flatMap(jda.findUser))
  }

  def update(channel: Channel, user: User) {
    cache(channel) = Some(user.typedId)
    // TODO: Do I need to await this?
    dbConfig.db.run(userByChannelTable.insertOrUpdate(channel.getGuild.typedId, channel.typedId, user.typedId))
  }

  def remove(channel: Channel): Unit = {
    cache(channel) = None
    // TODO: Do I need to await this?
    dbConfig.db.run(lookupQuery(channel.getGuild.typedId, channel.typedId).delete)
  }

  def remove(guild: ID[Guild], channel: ID[Channel]) {
    cache.updateById(channel, None)
    // TODO: Do I need to await this?
    dbConfig.db.run(lookupQuery(guild, channel).delete)
  }

  def all: Future[Seq[(ID[Guild], ID[Channel], ID[User])]] = dbConfig.db.run(userByChannelTable.result)
}

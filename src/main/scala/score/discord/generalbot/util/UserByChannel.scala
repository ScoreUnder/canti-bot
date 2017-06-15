package score.discord.generalbot.util

import net.dv8tion.jda.core.entities._
import score.discord.generalbot.wrappers.jda.Conversions._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class UserByChannel(dbConfig: DatabaseConfig[_ <: JdbcProfile], tableName: String) extends Iterable[((Long, Long), Long)] {

  import dbConfig.profile.api._

  private class UserByChannelTable(tag: Tag) extends Table[(Long, Long, Long)](tag, tableName) {
    val guildId = column[Long]("guild")
    val channelId = column[Long]("channel")
    val userId = column[Long]("user")
    val pk = primaryKey("primary", (guildId, channelId))

    override def * = (guildId, channelId, userId)
  }

  private val userByChannelTable = TableQuery[UserByChannelTable]

  Await.result(dbConfig.db.run(MTable.getTables).map(v => {
    val names = v.map(mt => mt.name.name)
    if (!names.contains(tableName)) {
      Await.result(dbConfig.db.run(userByChannelTable.schema.create), Duration.Inf)
    }
  }), Duration.Inf)

  private val userByGuildChannel = mutable.HashMap(
    Await.result(dbConfig.db.run(userByChannelTable.result), Duration.Inf) map {
      case (guild, channel, user) => (guild, channel) -> user
    }: _*
  )

  def apply(channel: Channel): Option[User] = {
    val guild = channel.getGuild
    apply(guild.id, channel.id) flatMap {
      userId => Option(guild.getJDA.getUserById(userId))
    }
  }

  def apply(guild: Long, channel: Long): Option[Long] = userByGuildChannel.get((guild, channel))

  def update(channel: Channel, user: User): Unit =
    update(channel.getGuild.id, channel.id, user.id)

  def update(guild: Long, channel: Long, user: Long) {
    userByGuildChannel((guild, channel)) = user
    // TODO: Do I need to await this?
    dbConfig.db.run(userByChannelTable.insertOrUpdate(guild, channel, user))
  }

  def remove(channel: Channel): Unit = remove(channel.getGuild.id, channel.id)

  def remove(guild: Long, channel: Long) {
    userByGuildChannel.remove((guild, channel))
    // TODO: Do I need to await this?
    dbConfig.db.run(userByChannelTable.filter(t => t.guildId === guild && t.channelId === channel).delete)
  }

  override def iterator = userByGuildChannel.iterator
}

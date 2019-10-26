package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.generalbot.collections.Cache
import score.discord.generalbot.util.DBUtils
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.IdConversions._
import score.discord.generalbot.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DatabaseMessageOwnership(dbConfig: DatabaseConfig[_ <: JdbcProfile],
                               cacheBase: Cache[ID[Message], Option[ID[User]]]) extends MessageOwnership {

  import dbConfig.profile.api._

  private class MessageOwnershipTable(tag: Tag, name: String) extends Table[(ID[Message], ID[User])](tag, name) {
    val messageId = column[ID[Message]]("message", O.PrimaryKey)
    val userId = column[ID[User]]("user")

    override def * = (messageId, userId)
  }

  private val database = dbConfig.db
  private val tableName = "message_ownership"
  private val messageOwnershipTable = TableQuery[MessageOwnershipTable](new MessageOwnershipTable(_: Tag, tableName))
  private val lookupQuery = Compiled((messageId: ConstColumn[ID[Message]]) => {
    messageOwnershipTable.filter(t => t.messageId === messageId).map(_.userId)
  })

  DBUtils.ensureTableCreated(dbConfig, messageOwnershipTable, tableName)

  private[this] val cache = new cacheBase.Backend[ID[Message]] {
    override def keyToId(key: ID[Message]): ID[Message] = key

    override def missing(key: ID[Message]): Future[Option[ID[User]]] =
      database.run(lookupQuery(key).result).map(_.headOption)
  }

  override def apply(messageId: ID[Message])(implicit jda: JDA): Future[Option[User]] =
    cache(messageId).map(idMaybe => idMaybe.flatMap(_.find))

  override def update(message: Message, user: User): Unit = {
    cache(message.id) = Some(user.id)
    database.run(messageOwnershipTable.insertOrUpdate(message.id, user.id))
  }

  override def remove(messageId: ID[Message]): Unit = {
    database.run(lookupQuery(messageId).delete).foreach { _ =>
      cache.invalidate(messageId)
    }
  }
}

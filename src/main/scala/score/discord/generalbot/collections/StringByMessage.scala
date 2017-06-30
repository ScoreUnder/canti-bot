package score.discord.generalbot.collections

import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.util.DBUtils
import score.discord.generalbot.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StringByMessage(dbConfig: DatabaseConfig[_ <: JdbcProfile],
                      cacheBase: Cache[ID[Message], Option[String]],
                      tableName: String) {

  import dbConfig.profile.api._

  private class StringByMessage(tag: Tag, name: String) extends Table[(ID[Message], String)](tag, name) {
    val messageId = column[ID[Message]]("message", O.PrimaryKey)
    val text = column[String]("text")

    override def * = (messageId, text)
  }

  private val database = dbConfig.db
  private val stringByMessage = TableQuery[StringByMessage](new StringByMessage(_: Tag, tableName))
  private val lookupQuery = Compiled({ (id: ConstColumn[ID[Message]]) =>
    stringByMessage.filter(t => t.messageId === id).map(_.text)
  })

  DBUtils.ensureTableCreated(dbConfig, stringByMessage, tableName)

  private[this] val cache = new cacheBase.Backend[ID[Message]] {
    override def keyToId(key: ID[Message]): ID[Message] = key

    override def missing(key: ID[Message]): Future[Option[String]] =
      database.run(lookupQuery(key).result).map(_.headOption)
  }

  def apply(messageId: ID[Message]): Future[Option[String]] = cache(messageId)

  def update(messageId: ID[Message], text: String) {
    cache(messageId) = Some(text)
    database.run(stringByMessage.insertOrUpdate(messageId, text))
  }

  def remove(messageId: ID[Message], invalidate: Boolean = false) {
    database.run(lookupQuery(messageId).delete).foreach { _ =>
      if (invalidate)
        cache.invalidate(messageId)
      else
        cache(messageId) = None
    }
  }
}

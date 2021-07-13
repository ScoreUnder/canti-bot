package score.discord.canti.collections

import net.dv8tion.jda.api.entities.Message
import score.discord.canti.util.DBUtils
import score.discord.canti.wrappers.database.IDMapping.*
import score.discord.canti.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StringByMessage(dbConfig: DatabaseConfig[? <: JdbcProfile], tableName: String)
    extends AsyncMap[ID[Message], String]:

  import dbConfig.profile.api.*

  private class StringByMessage(tag: Tag, name: String)
      extends Table[(ID[Message], String)](tag, name):
    val messageId = column[ID[Message]]("message", O.PrimaryKey)
    val text = column[String]("text")

    override def * = (messageId, text)

  private val database = dbConfig.db
  private val stringByMessage = TableQuery[StringByMessage](new StringByMessage(_: Tag, tableName))
  private val lookupQuery = Compiled({ id: ConstColumn[ID[Message]] =>
    stringByMessage.filter(t => t.messageId === id).map(_.text)
  })

  DBUtils.ensureTableCreated(dbConfig, stringByMessage, tableName)

  override def get(key: ID[Message]): Future[Option[String]] =
    database.run(lookupQuery(key).result).map(_.headOption)

  override def update(messageId: ID[Message], text: String): Future[Int] =
    database.run(stringByMessage.insertOrUpdate(messageId, text))

  override def remove(messageId: ID[Message]): Future[Int] =
    database.run(lookupQuery(messageId).delete)

  override def items: Future[Seq[(ID[Message], String)]] = throw new UnsupportedOperationException()

package score.discord.canti.collections

import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.canti.util.DBUtils
import score.discord.canti.wrappers.database.IDMapping._
import score.discord.canti.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserByMessage(dbConfig: DatabaseConfig[_ <: JdbcProfile], tableName: String)
    extends AsyncMap[ID[Message], ID[User]] {

  import dbConfig.profile.api._

  private class MessageOwnershipTable(tag: Tag, name: String)
      extends Table[(ID[Message], ID[User])](tag, name) {
    val messageId = column[ID[Message]]("message", O.PrimaryKey)
    val userId = column[ID[User]]("user")

    override def * = (messageId, userId)
  }

  private val database = dbConfig.db
  private val messageOwnershipTable =
    TableQuery[MessageOwnershipTable](new MessageOwnershipTable(_: Tag, tableName))
  private val lookupQuery = Compiled((messageId: ConstColumn[ID[Message]]) => {
    messageOwnershipTable.filter(t => t.messageId === messageId).map(_.userId)
  })

  DBUtils.ensureTableCreated(dbConfig, messageOwnershipTable, tableName)

  override def get(key: ID[Message]): Future[Option[ID[User]]] =
    database.run(lookupQuery(key).result).map(_.headOption)

  override def update(message: ID[Message], user: ID[User]): Future[Int] =
    database.run(messageOwnershipTable.insertOrUpdate(message, user))

  override def remove(messageId: ID[Message]): Future[Int] =
    database.run(lookupQuery(messageId).delete)

  override def items: Future[Seq[(ID[Message], ID[User])]] =
    throw new UnsupportedOperationException()
}

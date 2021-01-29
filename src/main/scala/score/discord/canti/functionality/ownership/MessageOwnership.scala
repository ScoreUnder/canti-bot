package score.discord.canti.functionality.ownership

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.canti.collections.AsyncMap
import score.discord.canti.wrappers.jda.Conversions._
import score.discord.canti.wrappers.jda.IdConversions._
import score.discord.canti.wrappers.jda.ID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageOwnership(backend: AsyncMap[_ >: ID[Message], ID[User]]) {
  def apply(message: Message): Future[Option[User]] = this(message.id)(message.getJDA)

  def apply(messageId: ID[Message])(implicit jda: JDA): Future[Option[User]] =
    backend.get(messageId).map(_.flatMap(_.find))

  def update(message: Message, user: User): Unit = backend(message.id) = user.id

  def remove(messageId: ID[Message]): Unit = backend.remove(messageId)
}

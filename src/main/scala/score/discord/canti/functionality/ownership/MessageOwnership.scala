package score.discord.canti.functionality.ownership

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.canti.collections.AsyncMap
import score.discord.canti.wrappers.jda.IdConversions.*
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichSnowflake.id

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageOwnership(backend: AsyncMap[? >: ID[Message], ID[User]]):
  def apply(message: Message): Future[Option[User]] = this(message.id)(using message.getJDA)

  def apply(messageId: ID[Message])(using JDA): Future[Option[User]] =
    backend.get(messageId).map(_.flatMap(_.find))

  def update(message: Message, user: User): Unit = backend(message.id) = user.id

  def remove(messageId: ID[Message]): Unit = backend.remove(messageId)

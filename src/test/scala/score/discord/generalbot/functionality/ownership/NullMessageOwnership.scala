package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.generalbot.wrappers.jda.ID

import scala.concurrent.Future

object NullMessageOwnership extends MessageOwnership {
  override def apply(messageId: ID[Message])(implicit jda: JDA): Future[Option[User]] = Future.successful(None)

  override def update(message: Message, user: User): Unit = {}

  override def remove(messageId: ID[Message]): Unit = {}
}

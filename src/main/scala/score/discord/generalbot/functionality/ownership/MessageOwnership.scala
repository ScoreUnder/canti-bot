package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

import scala.concurrent.Future

trait MessageOwnership {
  def apply(message: Message): Future[Option[User]] = this(message.getJDA, message.id)

  def apply(jda: JDA, messageId: ID[Message]): Future[Option[User]]

  def update(message: Message, user: User): Unit

  def remove(messageId: ID[Message]): Unit
}

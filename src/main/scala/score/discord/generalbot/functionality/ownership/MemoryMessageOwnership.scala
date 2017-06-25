package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.{Message, User}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

import scala.collection.mutable

class MemoryMessageOwnership(maxCapacity: Int) extends MessageOwnership {
  private[this] val backing = mutable.LinkedHashMap.empty[ID[Message], ID[User]]

  override def apply(jda: JDA, messageId: ID[Message]) = for {
    userId <- backing.get(messageId)
    user <- jda.findUser(userId)
  } yield user

  override def update(message: Message, user: User) {
    backing(message.id) = user.id
    while (backing.size > maxCapacity)
      backing -= backing.head._1
  }
}

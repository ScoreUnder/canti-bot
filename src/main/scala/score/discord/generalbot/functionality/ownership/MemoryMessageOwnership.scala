package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.{Message, User}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.collection.mutable

class MemoryMessageOwnership(maxCapacity: Int) extends MessageOwnership {
  private[this] val backing = mutable.LinkedHashMap.empty[Long, Long]

  override def apply(jda: JDA, messageId: Long) = for {
    userId <- backing.get(messageId)
    user <- Option(jda.getUserById(userId))
  } yield user

  override def update(message: Message, user: User) {
    backing(message.id) = user.id
    while (backing.size > maxCapacity)
      backing -= backing.head._1
  }
}

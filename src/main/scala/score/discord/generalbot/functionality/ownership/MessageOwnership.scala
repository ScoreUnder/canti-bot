package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.{Message, User}

trait MessageOwnership {
  def apply(message: Message): Option[User] = this(message.getJDA, message.getIdLong)

  def apply(jda: JDA, messageId: Long): Option[User]

  def update(message: Message, user: User): Unit
}

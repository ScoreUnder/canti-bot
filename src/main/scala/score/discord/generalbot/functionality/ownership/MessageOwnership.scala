package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.{Message, User}
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.Conversions._

trait MessageOwnership {
  def apply(message: Message): Option[User] = this(message.getJDA, message.typedId)

  def apply(jda: JDA, messageId: ID[Message]): Option[User]

  def update(message: Message, user: User): Unit
}

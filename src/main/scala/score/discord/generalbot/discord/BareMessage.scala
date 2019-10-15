package score.discord.generalbot.discord

import net.dv8tion.jda.api.entities.{Message, MessageChannel, User}
import score.discord.generalbot.wrappers.jda.ID

case class BareMessage(
  messageId: ID[Message],
  chanId: ID[MessageChannel],
  senderId: ID[User],
  text: String
)

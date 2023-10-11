package score.discord.canti.discord

import net.dv8tion.jda.api.entities.{Message, User}
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import score.discord.canti.wrappers.jda.ID

case class BareMessage(
  messageId: ID[Message],
  chanId: ID[MessageChannel],
  senderId: ID[User],
  text: String
)

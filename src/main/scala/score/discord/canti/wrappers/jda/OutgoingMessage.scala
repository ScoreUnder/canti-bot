package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.entities.Message.MentionType
import net.dv8tion.jda.api.utils.messages.MessageCreateData

final case class OutgoingMessage(
  message: MessageCreateData,
  files: Seq[(String, Array[Byte])] = Nil,
  ephemeral: Boolean = false
)

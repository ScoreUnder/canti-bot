package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.entities.Message.MentionType

final case class OutgoingMessage(
  message: Message,
  files: Seq[(String, Array[Byte])] = Nil,
  actionRows: Option[Seq[ActionRow]] = Some(Nil),
  allowedMentions: Seq[MentionType] = Nil,
  mentions: Seq[IMentionable] = Nil,
  // mentionRepliedUser?
)

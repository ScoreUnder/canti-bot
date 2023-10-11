package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.utils.messages.{MessageCreateData, MessageCreateBuilder}

object MessageConversions:
  trait MessageCreateFromX:
    def toMessageCreate: MessageCreateData

  class MessageFromString(me: String) extends MessageCreateFromX:
    def toMessageCreate = MessageCreateBuilder().setContent(me).nn.build.nn

  class MessageFromEmbedBuilder(me: EmbedBuilder) extends MessageCreateFromX:
    def toMessageCreate = MessageCreateBuilder().setEmbeds(me.build).nn.build.nn

  class MessageFromMessage(me: Message) extends MessageCreateFromX:
    def toMessageCreate = MessageCreateBuilder.fromMessage(me).nn.build.nn

  class MessageFromMessageCreateData(me: MessageCreateData) extends MessageCreateFromX:
    def toMessageCreate = me

  given Conversion[String, MessageCreateFromX] = MessageFromString(_)

  given Conversion[EmbedBuilder, MessageCreateFromX] = MessageFromEmbedBuilder(_)

  given Conversion[Message, MessageCreateFromX] = MessageFromMessage(_)

  given Conversion[MessageCreateData, MessageCreateFromX] = MessageFromMessageCreateData(_)

  given Conversion[MessageCreateFromX, OutgoingMessage] = x => OutgoingMessage(x.toMessageCreate)

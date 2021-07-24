package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.{EmbedBuilder, MessageBuilder}
import net.dv8tion.jda.api.entities.Message

object MessageConversions:
  trait MessageFromX:
    def toMessage: Message

  class MessageFromString(me: String) extends MessageFromX:
    def toMessage = MessageBuilder().append(me).build

  class MessageFromEmbedBuilder(me: EmbedBuilder) extends MessageFromX:
    def toMessage = MessageBuilder().setEmbeds(me.build).build

  class MessageFromMessage(me: Message) extends MessageFromX:
    def toMessage = me

  given Conversion[String, MessageFromX] = MessageFromString(_)

  given Conversion[EmbedBuilder, MessageFromX] = MessageFromEmbedBuilder(_)

  given Conversion[Message, MessageFromX] = MessageFromMessage(_)

  given Conversion[MessageFromX, OutgoingMessage] = x => OutgoingMessage(x.toMessage)

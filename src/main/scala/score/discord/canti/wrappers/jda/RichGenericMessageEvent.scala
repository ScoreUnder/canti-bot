package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.GenericMessageEvent

object RichGenericMessageEvent:
  extension (me: GenericMessageEvent)
    inline def messageId: ID[Message] = ID[Message](me.getMessageIdLong)

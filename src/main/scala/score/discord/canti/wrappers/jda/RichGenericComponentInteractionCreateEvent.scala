package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.GenericComponentInteractionCreateEvent

object RichGenericComponentInteractionCreateEvent:
  extension (me: GenericComponentInteractionCreateEvent)
    inline def messageId: ID[Message] = ID[Message](me.getMessageIdLong)

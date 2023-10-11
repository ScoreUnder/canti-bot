package score.discord.canti.wrappers.jda.matching

import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.emoji.Emoji

object React:
  object Text:
    def unapply(ev: MessageReaction): Option[String] =
      unapply(ev.getEmoji.nn)

    def unapply(emote: Emoji): Option[String] =
      Option.when(emote.getType == Emoji.Type.UNICODE)(emote.getName.nn)

package score.discord.generalbot.wrappers.jda.matching

import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote

object React {
  object Text {
    def unapply(ev: MessageReaction): Option[String] =
      unapply(ev.getReactionEmote)

    def unapply(emote: ReactionEmote): Option[String] =
      if (emote.isEmote) None
      else Some(emote.getName)
  }
}

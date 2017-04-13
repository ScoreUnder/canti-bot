package score.discord.generalbot.functionality

import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.wrappers.Conversions._

class TableFlip extends EventListener {
  val flip = "(╯°□°）╯︵ ┻━┻"
  val unflip = "┬─┬﻿ ノ( ゜-゜ノ)"

  override def onEvent(event: Event): Unit = {
    event match {
      case ev: MessageReceivedEvent =>
        val message = ev.getMessage
        if (message.getAuthor.isBot) return

        val text = message.getRawContent
        if (text contains flip) {
          message.getChannel ! unflip
        } else if (text contains unflip) {
          message.getChannel ! flip
        }

      case _ =>
    }
  }
}

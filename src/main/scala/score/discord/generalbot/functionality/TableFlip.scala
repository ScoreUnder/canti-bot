package score.discord.generalbot.functionality

import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.wrappers.jda.Conversions._

class TableFlip(implicit messageOwnership: MessageOwnership) extends EventListener {
  val flip = "(╯°□°）╯︵ ┻━┻"
  val unflip = "┬─┬﻿ ノ( ゜-゜ノ)"

  override def onEvent(event: Event): Unit = {
    event match {
      case ev: MessageReceivedEvent =>
        val message = ev.getMessage
        if (message.getAuthor.isBot) return

        val text = message.getContentRaw
        if (text contains flip) {
          message reply unflip
        } else if (text contains unflip) {
          message reply flip
        }

      case _ =>
    }
  }
}

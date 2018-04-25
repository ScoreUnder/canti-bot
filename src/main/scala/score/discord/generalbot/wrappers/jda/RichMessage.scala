package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.wrappers.jda.Conversions.{MessageFromX, _}

import scala.concurrent.Future

class RichMessage(val me: Message) extends AnyVal {
  def reply(contents: MessageFromX)(implicit mo: MessageOwnership): Future[Message] =
    me.getChannel.sendOwned(contents, me.getAuthor)
}

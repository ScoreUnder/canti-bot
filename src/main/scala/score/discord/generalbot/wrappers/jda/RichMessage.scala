package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.entities.{Guild, Message, TextChannel}
import score.discord.generalbot.collections.ReplyCache
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.chaining._

class RichMessage(val me: Message) extends AnyVal {
  /** Reply to this message.
    * Records the new message as being owned by the author of this message, and records it in the reply cache.
    *
    * @param contents contents of reply
    * @param mo message ownership cache
    * @param replyCache reply cache
    * @return the new Message, wrapped in Future
    */
  def reply(contents: MessageFromX)(implicit mo: MessageOwnership, replyCache: ReplyCache): Future[Message] =
    me.getChannel.sendOwned(contents, me.getAuthor).tap(_.foreach { message =>
      replyCache += me.id -> message.id
    })

  def guild: Option[Guild] =
    if (me.isFromGuild) Some(me.getGuild)
    else None

  def textChannel: Option[TextChannel] =
    if (me.isFromGuild) Some(me.getTextChannel)
    else None
}

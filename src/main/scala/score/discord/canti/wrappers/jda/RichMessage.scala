package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{Guild, Message, TextChannel}
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.wrappers.jda.Conversions._

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
  def !(contents: MessageFromX)(implicit mo: MessageOwnership, replyCache: ReplyCache): Future[Message] =
    me.reply(contents.toMessage).mentionRepliedUser(false).queueFuture()
      .recoverWith {
        case ex: ErrorResponseException if ex.getMeaning.contains("Unknown message") =>
          // TODO: once JDA gets a real ErrorResponse for this, target that instead of the "Unknown message" string
          me.getChannel.sendMessage(contents.toMessage).queueFuture()
      }
      .tap(_.foreach { message =>
        mo(message) = me.getAuthor
        replyCache += me.id -> message.id
      })

  def guild: Option[Guild] =
    if (me.isFromGuild) Some(me.getGuild)
    else None

  def textChannel: Option[TextChannel] =
    if (me.isFromGuild) Some(me.getTextChannel)
    else None
}

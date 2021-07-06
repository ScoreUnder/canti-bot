package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{Guild, Message, TextChannel}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.wrappers.jda.MessageConversions.MessageFromX
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichSnowflake.id

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.chaining.*

object RichMessage:
  extension (me: Message)
    /** Reply to this message. Records the new message as being owned by the author of this message,
      * and records it in the reply cache.
      *
      * @param contents
      *   contents of reply
      * @param mo
      *   message ownership cache
      * @param replyCache
      *   reply cache
      * @return
      *   the new Message, wrapped in Future
      */
    def !(contents: MessageFromX)(using MessageOwnership, ReplyCache): Future[Message] =
      me.reply(contents.toMessage).mentionRepliedUser(false).queueFuture().tap(registerReply)

    def registerReply(
      future: Future[Message]
    )(using mo: MessageOwnership, replyCache: ReplyCache): Future[Unit] =
      future.map { message =>
        mo(message) = me.getAuthor
        replyCache += me.id -> message.id
      }

    def guild: Option[Guild] =
      if me.isFromGuild then Some(me.getGuild)
      else None

    def textChannel: Option[TextChannel] =
      if me.isFromGuild then Some(me.getTextChannel)
      else None

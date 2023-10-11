package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{Guild, Message}
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.wrappers.jda.MessageConversions.MessageCreateFromX
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
    def !(contents: MessageCreateFromX)(using MessageOwnership, ReplyCache): Future[Message] =
      me.reply(contents.toMessageCreate).nn
        .mentionRepliedUser(false).nn
        .queueFuture().tap(registerReply)

    def registerReply(
      future: Future[Message]
    )(using mo: MessageOwnership, replyCache: ReplyCache): Future[Unit] =
      future.map { message =>
        mo(message) = me.getAuthor.nn
        replyCache += me.id -> message.id
      }

    def guild: Option[Guild] =
      if me.isFromGuild then Some(me.getGuild.nn)
      else None

    def guildMessageChannel: Option[GuildMessageChannel] =
      if me.isFromGuild then Some(me.getGuildChannel.nn)
      else None

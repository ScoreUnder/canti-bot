package score.discord.generalbot.command

import net.dv8tion.jda.client.entities.Group
import net.dv8tion.jda.core.entities._
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.hooks.EventListener
import net.dv8tion.jda.core.requests.ErrorResponse
import score.discord.generalbot.collections.{MessageCache, ReplyCache}
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.matching.Events.NonBotMessage

import scala.async.Async._
import scala.collection.GenIterable
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class QuoteCommand(implicit messageCache: MessageCache, val messageOwnership: MessageOwnership, val replyCache: ReplyCache) extends Command.Anyone with ReplyingCommand {
  override def name: String = "quote"

  override val aliases: GenIterable[String] = List("q")

  override def description: String = "Embed a message as a quote"

  override def longDescription(invocation: String) =
    s"""Usage:
       |`$invocation 12341234`
       |If 12341234 is a message ID, the corresponding message will be embedded as a quote.
       |You may quote from other channels by shift-clicking "Copy ID" in Discord and using that extended message ID.
       |You can also specify the channel manually, if you do not use the extended ID:
       |`$invocation 12341234 #general`
       |This command may also be invoked with 4chan-style post ID quotes:
       |`>>12341234`
    """.stripMargin

  def executeAndGetMessage(cmdMessage: Message, args: String): Future[Message] = {
    async {
      val (quoteIdMaybe, specifiedChannel) = parseQuoteIDs(args)
      (quoteIdMaybe match {
        case Some(quoteId) =>
          val jda = cmdMessage.getJDA
          val channel =
            specifiedChannel match {
              case Some(chanID) =>
                Option(jda.getTextChannelById(chanID.value))
                  .orElse(Option(jda.getPrivateChannelById(chanID.value)))
              case None =>
                messageCache
                  .find(_.messageId == quoteId)
                  .map(m => m.chanId)
                  .flatMap(ch => Option(jda.getTextChannelById(ch.value)))
                  .orElse(Some(cmdMessage.getChannel))
            }

          checkChannelVisibility(channel, cmdMessage.getAuthor) match {
            case Right(ch) =>
              import APIHelper.Error
              import ErrorResponse._
              val foundMessage = APIHelper.tryRequest(ch getMessageById quoteId.value).map(Right(_)).recover {
                case Error(UNKNOWN_MESSAGE) if specifiedChannel.isEmpty =>
                  Left("Can't find the channel that message is in. Try specifying it manually.")
                case Error(UNKNOWN_MESSAGE) =>
                  Left("Can't find that message in the channel specified.")
                case Error(UNKNOWN_CHANNEL) =>
                  Left("Can't find that channel.")
                case Error(MISSING_PERMISSIONS) | Error(MISSING_ACCESS) | _: PermissionException =>
                  Left("I don't have permission to read messages in that channel.")
              }

              val futureReply = for (message <- foundMessage) yield message match {
                case Right(msg) => getMessageAsQuote(cmdMessage, ch, msg)
                case Left(err) => BotMessages.error(err)
              }
              await(futureReply)

            case Left(err) =>
              BotMessages.error(err)
          }
        case None =>
          BotMessages.error("You need to give a message ID to quote")
      }).toMessage
    }
  }

  private def checkChannelVisibility(channel: Option[MessageChannel], sender: User) = {
    channel match {
      case Some(ch: Channel) if sender canSee ch => Right(ch)
      case Some(ch: Group) if sender canSee ch => Right(ch)
      case Some(ch: PrivateChannel) if ch.getUser == sender => Right(ch)
      case Some(_) => Left("You do not have access to the specified channel.")
      case None => Left("I do not have access to the specified channel.")
    }
  }

  private def getMessageAsQuote(cmdMessage: Message, ch: MessageChannel, msg: Message) = {
    val chanName = Option(ch.getName).map("#" + _).getOrElse("Untitled channel")
    val sender = msg.getAuthor

    val quote = BotMessages
      .plain(s"[🔗 Go to message ↦](${msg.getJumpUrl})\n${msg.getContentRaw}")
      .setAuthor(sender.getName, null, sender.getAvatarUrl)
      .setTimestamp(msg.getCreationTime)
      .setFooter(s"$chanName | Requested by ${cmdMessage.getAuthor.mentionAsText}", null)

    val embeds = msg.getEmbeds.asScala

    embeds.flatMap(emb => Option(emb.getImage)).headOption match {
      case Some(image) => quote.setImage(image.getUrl)
      case None =>
    }

    // Overwrite embed preview image with uploaded image if applicable
    // as uploaded image is "more important"
    msg.getAttachments.asScala.find(_.isImage) match {
      case Some(image) => quote.setImage(image.getUrl)
      case None =>
    }

    for (embed <- embeds; desc <- Option(embed.getDescription)) {
      quote.addField("[Embed description]", desc, false)
    }

    for (embed <- embeds; field <- embed.getFields.asScala) {
      quote.addField(field)
    }

    quote
  }

  private def parseQuoteIDs(args: String) = {
    val (firstIdStr, remains) = args.trim.span(Character.isDigit)
    val secondIdStr = remains.drop(1).takeWhile(Character.isDigit)

    // If shift+click was used to copy a long ID
    if (remains.startsWith("-") && secondIdStr.nonEmpty) {
      (Some(ID.fromString[Message](secondIdStr)), Some(ID.fromString[TextChannel](firstIdStr)))
    } else if (firstIdStr.nonEmpty) {
      val quoteId = ID.fromString[Message](firstIdStr)
      val specifiedChannel = QuoteCommand.CHANNEL_REGEX
        .findPrefixMatchOf(remains)
        .map(m => ID.fromString[TextChannel](m.group(1)))
      (Some(quoteId), specifiedChannel)
    } else {
      args match {
        case QuoteCommand.LINK_REGEX(channelId, messageId) =>
          (Some(ID.fromString[Message](messageId)), Some(ID.fromString[Channel](channelId)))
        case _ =>
          (None, None)
      }
    }
  }

  class GreentextListener extends EventListener {
    override def onEvent(event: Event): Unit = event match {
      case NonBotMessage(message) =>
        QuoteCommand.GREENTEXT_REGEX
          .findPrefixMatchOf(message.getContentRaw)
          .foreach(m => QuoteCommand.this.execute(message, m.after.toString))
      case _ =>
    }
  }

}

object QuoteCommand {
  private val LINK_REGEX_STR = """https://(?:[^.]+\.)?discordapp\.com/channels/\d+/(\d+)/(\d+)"""
  private val LINK_REGEX = s"^$LINK_REGEX_STR".r.unanchored
  private val CHANNEL_REGEX = "\\s*<#(\\d+)>".r
  // To avoid false positives, trigger on message URL or 9+ digits
  // 1 second past discord epoch is already 10 digits in their snowflake format.
  private val GREENTEXT_REGEX = s">>(?=\\d{9,}|$LINK_REGEX_STR)".r
}

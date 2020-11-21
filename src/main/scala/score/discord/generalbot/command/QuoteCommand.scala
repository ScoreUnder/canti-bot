package score.discord.generalbot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities._
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.ErrorResponse
import score.discord.generalbot.collections.{MessageCache, ReplyCache}
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.IdConversions._
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.matching.Events.NonBotMessage

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class QuoteCommand(implicit messageCache: MessageCache, val messageOwnership: MessageOwnership, val replyCache: ReplyCache) extends Command.Anyone with ReplyingCommand {
  override def name: String = "quote"

  override val aliases: Seq[String] = List("q")

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
      (parseQuoteIDs(args) match {
        case Some((quoteId, specifiedChannel)) =>
          val channel = channelOrBestGuess(cmdMessage, quoteId, specifiedChannel)
          checkChannelVisibility(channel, cmdMessage.getAuthor) match {
            case Right(ch) =>
              val foundMessage = await(APIHelper
                .tryRequest(ch retrieveMessageById quoteId.value)
                .map(Right(_))
                .recover(stringifyMessageRetrievalError(specifiedChannel)))

              for (message <- foundMessage)
                yield getMessageAsQuote(cmdMessage, ch, message)
            case Left(e) => Left(e)
          }
        case None =>
          Left("You need to give a message ID to quote")
      }).fold(BotMessages.error, identity).toMessage
    }
  }

  private def channelOrBestGuess(context: Message, quoteId: ID[Message], specifiedChannel: Option[ID[MessageChannel]]): Option[MessageChannel] = {
    implicit val jda: JDA = context.getJDA
    specifiedChannel match {
      case Some(chanID) => chanID.find
      case None =>
        messageCache
          .find(_.messageId == quoteId)
          .map(m => m.chanId)
          .flatMap(_.find)
          .orElse(Some(context.getChannel))
    }
  }

  private def stringifyMessageRetrievalError(specifiedChannel: Option[ID[MessageChannel]]): PartialFunction[Throwable, Either[String, Nothing]] = {
    import APIHelper.Error
    import ErrorResponse._
    {
      case Error(UNKNOWN_MESSAGE) if specifiedChannel.isEmpty =>
        Left("Can't find the channel that message is in. Try specifying it manually.")
      case Error(UNKNOWN_MESSAGE) =>
        Left("Can't find that message in the channel specified.")
      case Error(UNKNOWN_CHANNEL) =>
        Left("Can't find that channel.")
      case Error(MISSING_PERMISSIONS) | Error(MISSING_ACCESS) | _: PermissionException =>
        Left("I don't have permission to read messages in that channel.")
    }
  }

  private def checkChannelVisibility(channel: Option[MessageChannel], sender: User) = {
    channel match {
      case Some(ch: GuildChannel) if sender canSee ch => Right(ch)
      case Some(ch: PrivateChannel) if ch.getUser == sender => Right(ch)
      case Some(_) => Left("You do not have access to the specified channel.")
      case None => Left("I do not have access to the specified channel.")
    }
  }

  private def getMessageAsQuote(cmdMessage: Message, ch: MessageChannel, msg: Message) = {
    val chanName = Option(ch.getName).fold("Untitled channel")("#" + _)
    val sender = msg.getAuthor

    val quote = BotMessages
      .plain(s"[🔗 Go to message ↦](${msg.getJumpUrl})\n${msg.getContentRaw}")
      .setAuthor(sender.getName, null, sender.getAvatarUrl)
      .setTimestamp(msg.getTimeCreated)
      .setFooter(s"$chanName | Requested by ${cmdMessage.getAuthor.mentionAsText}", null)

    val embeds = msg.getEmbeds.asScala

    msg.getAttachments.asScala.find(_.isImage) match {
      case Some(image) => quote.setImage(image.getUrl)
      case None =>
        embeds.flatMap(emb => Option(emb.getImage)).headOption.foreach { image =>
          quote.setImage(image.getUrl)
        }
    }

    embeds.foreach { embed =>
      Option(embed.getDescription).foreach { desc =>
        quote.addField("[Embed description]", desc, false)
      }

      embed.getFields.asScala.foreach(quote.addField)
    }

    quote
  }

  private def parseQuoteIDs(args: String) = {
    val (firstIdStr, remains) = args.trim.span(Character.isDigit)
    val secondIdStr = remains.drop(1).takeWhile(Character.isDigit)

    // If shift+click was used to copy a long ID
    if (remains.startsWith("-") && secondIdStr.nonEmpty) {
      Some((ID.fromString[Message](secondIdStr), Some(ID.fromString[MessageChannel](firstIdStr))))
    } else if (firstIdStr.nonEmpty) {
      val quoteId = ID.fromString[Message](firstIdStr)
      val specifiedChannel = QuoteCommand.CHANNEL_REGEX
        .findPrefixMatchOf(remains)
        .map(m => ID.fromString[TextChannel](m.group(1)))
      Some((quoteId, specifiedChannel))
    } else {
      args match {
        case QuoteCommand.LINK_REGEX(channelId, messageId) =>
          Some((ID.fromString[Message](messageId), Some(ID.fromString[MessageChannel](channelId))))
        case _ =>
          None
      }
    }
  }

  class GreentextListener extends EventListener {
    override def onEvent(event: GenericEvent): Unit = event match {
      case NonBotMessage(message) =>
        QuoteCommand.GREENTEXT_REGEX
          .findPrefixMatchOf(message.getContentRaw)
          .foreach(m => QuoteCommand.this.execute(message, m.after.toString))
      case _ =>
    }
  }

}

object QuoteCommand {
  private val LINK_REGEX_STR = """https://(?:[^.]+\.)?discord(?:app)?\.com/channels/\d+/(\d+)/(\d+)"""
  private val LINK_REGEX = s"^$LINK_REGEX_STR".r.unanchored
  private val CHANNEL_REGEX = "\\s*<#(\\d+)>".r
  // To avoid false positives, trigger on message URL or 9+ digits
  // 1 second past discord epoch is already 10 digits in their snowflake format.
  private val GREENTEXT_REGEX = s">>(?=\\d{9,}|$LINK_REGEX_STR)".r
}

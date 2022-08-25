package score.discord.canti.command

import com.codedx.util.MapK
import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.{JDA, MessageBuilder}
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.components.{ActionRow, Button}
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.restaction.MessageAction
import score.discord.canti.collections.{MessageCache, ReplyCache}
import score.discord.canti.command.api.{
  ArgSpec, ArgType, CommandInvocation, CommandInvoker, CommandPermissions, MessageInvoker
}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.util.StringUtils.trimToSize
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.{ID, RetrievableMessage}
import score.discord.canti.wrappers.jda.IdConversions.*
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RichMessageChannel.findMessage
import score.discord.canti.wrappers.jda.RichUser.{canSee, mentionAsText}
import score.discord.canti.wrappers.jda.matching.Events.NonBotMessage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.jdk.CollectionConverters.*

class QuoteCommand(messageCache: MessageCache)(using MessageOwnership, ReplyCache)
    extends GenericCommand:
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

  override def permissions = CommandPermissions.Anyone

  // TODO: make this into its own ArgType
  private val quoteArg =
    ArgSpec("message", "ID or link to the message to quote", ArgType.GreedyString)

  override val argSpec = List(quoteArg)

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    async {
      val quotedMsg = await(retrieveQuoteMessageByArg(ctx.invoker, ctx.args(quoteArg)))
      val buttons = quotedMsg.toOption.toList.map { msg =>
        ActionRow.of(Button.link(msg.getJumpUrl, "Go to message"))
      }
      val replyMsg = quotedMsg
        .map(getMessageAsQuote(ctx.invoker, _))
        .fold(e => MessageBuilder(BotMessages.error(e)), MessageBuilder(_))
        .setActionRows(buttons*)
        .build
      await(ctx.invoker.reply(replyMsg))
    }

  private def retrieveQuoteMessageByArg(
    invoker: CommandInvoker,
    args: String
  ): Future[Either[String, Message]] =
    async {
      given JDA = invoker.user.getJDA
      parseQuoteIDs(args) match
        case Some((quoteId, specifiedChannel)) =>
          val channel = channelOrBestGuess(invoker.channel, quoteId, specifiedChannel)
          checkChannelVisibility(channel, invoker.user) match
            case Right(ch) =>
              await(
                ch.findMessage(quoteId)
                  .map(Right(_))
                  .recover(stringifyMessageRetrievalError(specifiedChannel))
              )
            case Left(e) => Left(e)
        case None =>
          Left("You need to give a message ID to quote")
    }

  private def channelOrBestGuess(
    origChannel: Option[MessageChannel],
    quoteId: ID[Message],
    specifiedChannel: Option[ID[MessageChannel]]
  )(using JDA): Option[MessageChannel] =
    specifiedChannel match
      case Some(chanID) => chanID.find
      case None =>
        messageCache
          .find(_.messageId == quoteId)
          .map(m => m.chanId)
          .flatMap(_.find)
          .orElse(origChannel)

  private def stringifyMessageRetrievalError(
    specifiedChannel: Option[ID[MessageChannel]]
  ): PartialFunction[Throwable, Either[String, Nothing]] =
    import APIHelper.Error
    import ErrorResponse.*
    {
      case Error(UNKNOWN_MESSAGE) if specifiedChannel.isEmpty =>
        Left("Can't find the channel that message is in. Try specifying it manually.")
      case Error(UNKNOWN_MESSAGE) =>
        Left("Can't find that message in the channel specified.")
      case Error(UNKNOWN_CHANNEL) =>
        Left("Can't find that channel.")
      case Error(MISSING_PERMISSIONS) | Error(MISSING_ACCESS) =>
        Left("I don't have permission to read messages in that channel.")
      case e: PermissionException =>
        Left(
          s"I don't have permission to read messages in that channel. Missing `${e.getPermission.nn.getName}`." // TODO: PR @Nonnull for getPermission
        )
    }

  private def checkChannelVisibility(channel: Option[MessageChannel], sender: User) =
    channel match
      case Some(ch: GuildChannel) if sender.canSee(ch)      => Right(ch)
      case Some(ch: PrivateChannel) if ch.getUser == sender => Right(ch)
      case Some(_) => Left("You do not have access to the specified channel.")
      case None    => Left("I do not have access to the specified channel.")

  private def getMessageAsQuote(invoker: CommandInvoker, msg: Message) =
    val ch = msg.getChannel
    val chanName = Option(ch.getName).fold("Untitled channel")("#" + _)
    val sender = msg.getAuthor

    val quote = BotMessages
      .plain(msg.getContentRaw)
      .setAuthor(sender.getName, null, sender.getAvatarUrl)
      .setTimestamp(msg.getTimeCreated)
      .setFooter(s"$chanName | Requested by ${invoker.user.mentionAsText}", null)

    val embeds = msg.getEmbeds.asScala

    msg.getAttachments.asScala.find(_.isImage) match
      case Some(image) => quote.setImage(image.getUrl)
      case None =>
        for image <- embeds.flatMap(_.getImage.?).headOption do quote.setImage(image.getUrl)

    for
      embed <- embeds
      desc <- embed.getDescription.?
    do
      quote.addField("[Embed description]", trimToSize(desc, chars = 1000, lines = 4), false)

      embed.getFields.asScala.foreach(quote.addField)

    for sticker <- msg.getStickers.asScala.toSeq do
      quote.addField(s"[Sticker: ${sticker.getName}]", sticker.getDescription, true)

    quote.toMessage

  private def parseQuoteIDs(args: String) =
    val (firstIdStr, remains) = args.trimnn.span(Character.isDigit)
    val secondIdStr = remains.drop(1).takeWhile(Character.isDigit)

    // If shift+click was used to copy a long ID
    if remains.startsWith("-") && secondIdStr.nonEmpty then
      Some((ID.fromString[Message](secondIdStr), Some(ID.fromString[MessageChannel](firstIdStr))))
    else if firstIdStr.nonEmpty then
      val quoteId = ID.fromString[Message](firstIdStr)
      val specifiedChannel = QuoteCommand.CHANNEL_REGEX
        .findPrefixMatchOf(remains)
        .map(m => ID.fromString[TextChannel](m.group(1)))
      Some((quoteId, specifiedChannel))
    else
      args match
        case QuoteCommand.LINK_REGEX(channelId, messageId) =>
          Some((ID.fromString[Message](messageId), Some(ID.fromString[MessageChannel](channelId))))
        case _ =>
          None

  class GreentextListener extends EventListener:
    private val logger = loggerOf[GreentextListener]

    override def onEvent(event: GenericEvent): Unit = event match
      case NonBotMessage(message) =>
        Future {
          QuoteCommand.GREENTEXT_REGEX
            .findPrefixMatchOf(message.getContentRaw)
            .foreach { m =>
              val invocation =
                CommandInvocation(
                  "",
                  ">>",
                  MapK.empty + (quoteArg, m.after.toString),
                  MessageInvoker(message)
                )
              logger.debug(s"running command: $invocation")
              QuoteCommand.this.execute(invocation)
            }
        }
      case _ =>
end QuoteCommand

object QuoteCommand:
  private val LINK_REGEX_STR =
    """https://(?:[^.]+\.)?discord(?:app)?\.com/channels/\d+/(\d+)/(\d+)"""
  private val LINK_REGEX = s"^$LINK_REGEX_STR".r.unanchored
  private val CHANNEL_REGEX = "\\s*<#(\\d+)>".r
  // To avoid false positives, trigger on message URL or 9+ digits
  // 1 second past discord epoch is already 10 digits in their snowflake format.
  private val GREENTEXT_REGEX = s">>\\s*(?=\\d{9,}|$LINK_REGEX_STR)".r

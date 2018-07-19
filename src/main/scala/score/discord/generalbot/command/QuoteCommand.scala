package score.discord.generalbot.command
import net.dv8tion.jda.client.entities.Group
import net.dv8tion.jda.core.entities._
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.hooks.EventListener
import net.dv8tion.jda.core.requests.ErrorResponse
import score.discord.generalbot.collections.MessageCache
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.matching.Events.NonBotMessage

import scala.async.Async._
import scala.collection.GenIterable
import scala.concurrent.ExecutionContext.Implicits.global

class QuoteCommand(commands: Commands, messageCache: MessageCache)(implicit messageOwnership: MessageOwnership) extends Command.Anyone {
  override def name: String = "quote"

  override val aliases: GenIterable[String] = List("q")

  override def description: String = "Embed a message as a quote"

  override val longDescription =
    s"""Usage:
       |`${commands.prefix}$name 12341234`
       |If 12341234 is a message ID, the corresponding message will be embedded as a quote.
       |As Discord provides no way to look up a message by ID alone, you may need to specify the channel too:
       |`${commands.prefix}$name 12341234 #general`
       |This command may also be invoked with 4chan-style post ID quotes:
       |`>>12341234`
    """.stripMargin

  override def execute(cmdMessage: Message, args: String): Unit = {
    async {
      val (quoteId, specifiedChannel) = parseQuoteIDs(args)
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


      val sender = cmdMessage.getAuthor
      val allowedChannel = channel match {
        case Some(ch: Channel) if sender canSee ch => Right(ch)
        case Some(ch: Group) if sender canSee ch => Right(ch)
        case Some(ch: PrivateChannel) if ch.getUser == sender => Right(ch)
        case Some(_) => Left("You do not have access to the specified channel.")
        case None => Left("I do not have access to the specified channel.")
      }
      val botReply = allowedChannel match {
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

          for {
            message <- foundMessage
            reply <- cmdMessage reply {
              message match {
                case Right(msg) => getMessageAsQuote(cmdMessage, ch, msg)
                case Left(err) => BotMessages.error(err)
              }
            }
          } yield reply
        case Left(err) =>
          cmdMessage reply BotMessages.error(err)
      }
      await(botReply)
    }.failed.foreach(APIHelper.loudFailure("quoting a message", cmdMessage.getChannel))
  }

  private def getMessageAsQuote(cmdMessage: Message, ch: MessageChannel, msg: Message) = {
    val chanName = Option(ch.getName).map("#" + _).getOrElse("Untitled channel")
    val sender = msg.getAuthor
    BotMessages
      .plain(msg.getContentRaw)
      .setAuthor(sender.getName, null, sender.getAvatarUrl)
      .setTimestamp(msg.getCreationTime)
      .setFooter(s"$chanName | Requested by ${cmdMessage.getAuthor.mentionAsText}", null)
  }

  private def parseQuoteIDs(args: String) = {
    val (firstIdStr, remains) = args.trim.span(Character.isDigit)
    val secondIdStr = remains.drop(1).takeWhile(Character.isDigit)

    // If shift+click was used to copy a long ID
    if (remains.startsWith("-") && secondIdStr.nonEmpty) {
      (ID.fromString[Message](secondIdStr), Some(ID.fromString[TextChannel](firstIdStr)))
    } else {
      val quoteId = ID.fromString[Message](firstIdStr)
      val specifiedChannel = QuoteCommand.CHANNEL_REGEX
        .findPrefixMatchOf(remains)
        .map(m => ID.fromString[TextChannel](m.group(1)))
      (quoteId, specifiedChannel)
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
  private val CHANNEL_REGEX = "\\s*<#(\\d+)>".r
  // To avoid false positives, trigger on 9+ digits
  // 1 second past discord epoch is already 10 digits in their snowflake format.
  private val GREENTEXT_REGEX = ">>(?=\\d{9,})".r
}

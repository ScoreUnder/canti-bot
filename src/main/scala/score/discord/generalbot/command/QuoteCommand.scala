package score.discord.generalbot.command
import net.dv8tion.jda.client.entities.Group
import net.dv8tion.jda.core.entities.{Channel, Message, MessageChannel, PrivateChannel}
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.hooks.EventListener
import net.dv8tion.jda.core.requests.ErrorResponse
import score.discord.generalbot.collections.MessageCache
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.matching.Events.NonBotMessage

import scala.async.Async._
import scala.collection.GenIterable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class QuoteCommand(messageCache: MessageCache)(implicit messageOwnership: MessageOwnership) extends Command.Anyone {
  override def name: String = "quote"

  override val aliases: GenIterable[String] = List("quote")

  override def description: String = "Embed a message as a quote"

  override def execute(cmdMessage: Message, args: String): Unit = {
    def replyFromChannel(ch: MessageChannel, specifiedChannel: Option[_], quoteId: ID[Message]): Future[Message] = {
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
            case Right(msg) =>
              val chanName = Option(ch.getName).map("#" + _).getOrElse("Untitled channel")
              val sender = msg.getAuthor
              BotMessages
                .plain(msg.getContentRaw)
                .setAuthor(sender.getName, null, sender.getAvatarUrl)
                .setTimestamp(msg.getCreationTime)
                .setFooter(s"$chanName | Requested by ${cmdMessage.getAuthor.mentionAsText}", null)
            case Left(err) =>
              BotMessages.error(err)
          }
        }
      } yield reply
    }

    async {
      val (quoteIdStr, remains) = args.trim.span(Character.isDigit)
      val quoteId = ID.fromString[Message](quoteIdStr)
      val specifiedChannel = QuoteCommand.CHANNEL_REGEX.findPrefixMatchOf(remains)
      val channel =
        specifiedChannel match {
          case Some(regexMatch) =>
            Option(cmdMessage.getJDA.getTextChannelById(regexMatch.group(1)))
          case None =>
            messageCache
              .find(_.messageId == quoteId)
              .map(m => m.chanId)
              .flatMap(ch => Option(cmdMessage.getJDA.getTextChannelById(ch.value)))
              .orElse(Some(cmdMessage.getChannel))
        }


      val sender = cmdMessage.getAuthor
      channel match {
        case Some(ch: Channel) if sender canSee ch => await(replyFromChannel(ch, specifiedChannel, quoteId))
        case Some(ch: Group) if sender canSee ch => await(replyFromChannel(ch, specifiedChannel, quoteId))
        case Some(ch: PrivateChannel) if ch.getUser == sender => await(replyFromChannel(ch, specifiedChannel, quoteId))
        case Some(_) =>
          cmdMessage reply BotMessages.error("You do not have access to the specified channel.")
        case None =>
          cmdMessage reply BotMessages.error("I do not have access to the specified channel.")
      }
    }.failed.foreach(APIHelper.loudFailure("quoting a message", cmdMessage.getChannel))
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
  private val GREENTEXT_REGEX = ">>(?=\\d)".r
}

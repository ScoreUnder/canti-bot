package score.discord.canti.command.api

import net.dv8tion.jda.api.interactions.commands.CommandInteraction
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.entities.{Member, Message, User}
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.requests.ErrorResponse.{MISSING_PERMISSIONS, UNKNOWN_MESSAGE}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.command.api.TypingManager
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper.Error
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.jda.{ID, MessageReceiver, RetrievableMessage}
import score.discord.canti.wrappers.jda.MessageConversions.*
import score.discord.canti.wrappers.jda.OutgoingMessage
import score.discord.canti.wrappers.jda.RichMessage.!
import score.discord.canti.wrappers.jda.RichMessageChannel.editMessage
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

trait CommandInvoker:
  def user: User = getUser()

  def member: Either[String, Member] =
    getMember() ?<> "You can only use this command from within a server."

  def channel: Option[MessageChannel] = getChannel().?

  protected def getUser(): User

  protected def getMember(): Member | Null

  protected def getChannel(): MessageChannel | Null

  def replyLater(transientIfPossible: Boolean)(using Scheduler): Future[Unit]

  def asMessageReceiver: MessageReceiver

  def originatingMessage: Option[Message] = None

  final def reply(message: OutgoingMessage): Future[RetrievableMessage] =
    asMessageReceiver.sendMessage(message)

  final def reply(message: MessageCreateFromX): Future[RetrievableMessage] =
    asMessageReceiver.sendMessage(message)

private abstract class TypingCommandInvoker(channel: MessageChannel) extends CommandInvoker:
  protected val typingManager = TypingManager(channel)

  override def replyLater(transientIfPossible: Boolean)(using Scheduler): Future[Unit] =
    typingManager.sendTypingNotification()

final case class MessageInvoker(origin: Message)(using MessageOwnership, ReplyCache)
    extends TypingCommandInvoker(origin.getChannel.nn):
  export origin.{getChannel, getMember}

  override protected def getUser(): User = origin.getAuthor.nn

  override def originatingMessage = Some(origin)

  override val asMessageReceiver: MessageReceiver = message =>
    typingManager.completeWith {
      MessageReceiver(origin).sendMessage(message)
    }

final case class SlashCommandInvoker(origin: SlashCommandInteraction)(using
  MessageOwnership,
  ReplyCache
) extends CommandInvoker:
  export origin.{getChannel, getMember}

  override protected def getUser(): User = origin.getUser.nn

  private var deferredReply: Option[Future[InteractionHook]] = None

  override def replyLater(transientIfPossible: Boolean)(using Scheduler): Future[Unit] =
    synchronized {
      val future = origin.deferReply(transientIfPossible).nn.queueFuture()
      deferredReply = Some(future)
      future.map(_ => ())(using ExecutionContext.parasitic)
    }

  private def getDeferredReply(): Future[MessageReceiver] =
    synchronized {
      deferredReply match
        case Some(future) => future.map(MessageReceiver(_))(using ExecutionContext.parasitic)
        case None         => Future.successful(MessageReceiver(origin))
    }

  override val asMessageReceiver: MessageReceiver = message =>
    for
      hook <- getDeferredReply()
      msg <- hook.sendMessage(message)
    yield msg

final case class EditedMessageInvoker(origin: Message, myMessage: ID[Message])(using
  MessageOwnership,
  ReplyCache
) extends TypingCommandInvoker(origin.getChannel.nn):
  export origin.{getChannel, getMember}

  override protected def getUser(): User = origin.getAuthor.nn

  override def originatingMessage = Some(origin)

  private val replied = AtomicBoolean(false)

  override val asMessageReceiver: MessageReceiver = message =>
    typingManager.completeWith {
      if replied.getAndSet(true) then MessageReceiver(origin).sendMessage(message)
      else
        MessageReceiver
          .intoEdit(origin.getChannel.nn, myMessage)
          .sendMessage(message)
          .recoverWith { case Error(UNKNOWN_MESSAGE) =>
            reply(message)
          }
    }

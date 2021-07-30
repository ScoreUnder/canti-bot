package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.{Message, MessageChannel}
import net.dv8tion.jda.api.requests.restaction.MessageAction
import net.dv8tion.jda.api.interactions.{Interaction, InteractionHook}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.wrappers.jda.MessageConversions.MessageFromX
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichSnowflake.id
import score.discord.canti.wrappers.jda.RetrievableMessage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction

trait MessageReceiver:
  def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage]

private class InteractionMessageReceiver(origin: Interaction) extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val req = origin.reply(outgoingMessage.message)
    for case (name, data) <- outgoingMessage.files do req.addFile(data, name)
    req.queueFuture().map(RetrievableMessage(_))(using ExecutionContext.parasitic)

private class InteractionHookMessageReceiver(origin: InteractionHook) extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val req = origin.sendMessage(outgoingMessage.message)
    for case (name, data) <- outgoingMessage.files do req.addFile(data, name)
    req.queueFuture().map(RetrievableMessage(_))(using ExecutionContext.parasitic)

private class MessageReplyMessageReceiver(origin: Message)(using
  messageOwnership: MessageOwnership,
  replyCache: ReplyCache
) extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val req = origin.reply(outgoingMessage.message)
    MessageReceiver.applyOutgoingMessage(req, outgoingMessage)
    val future = req.queueFuture()
    for message <- future do
      messageOwnership(message) = origin.getAuthor
      replyCache += origin.id -> message.id
    future.map(RetrievableMessage(_))(using ExecutionContext.parasitic)

private class MessageChannelMessageReceiver(origin: MessageChannel) extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val req = origin.sendMessage(outgoingMessage.message)
    MessageReceiver.applyOutgoingMessage(req, outgoingMessage)
    req.queueFuture().map(RetrievableMessage(_))(using ExecutionContext.parasitic)

private class MessageEditMessageReceiver(channel: MessageChannel, messageID: ID[Message])
    extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val req = channel.editMessageById(messageID.value, outgoingMessage.message)
    MessageReceiver.applyOutgoingMessage(req, outgoingMessage)
    req.queueFuture().map(RetrievableMessage(_))(using ExecutionContext.parasitic)

object MessageReceiver:
  def apply(origin: Interaction): MessageReceiver = InteractionMessageReceiver(origin)
  def apply(origin: InteractionHook): MessageReceiver = InteractionHookMessageReceiver(origin)
  def apply(
    origin: Message
  )(using messageOwnership: MessageOwnership, replyCache: ReplyCache): MessageReceiver =
    MessageReplyMessageReceiver(origin)
  def apply(origin: MessageChannel): MessageReceiver = MessageChannelMessageReceiver(origin)
  def intoEdit(origin: Message): MessageReceiver =
    MessageEditMessageReceiver(origin.getChannel, origin.id)
  def intoEdit(channel: MessageChannel, messageID: ID[Message]): MessageReceiver =
    MessageEditMessageReceiver(channel, messageID)

  object NullReceiver extends MessageReceiver:
    def sendMessage(outgoingMessage: OutgoingMessage) = Future.never

  def applyOutgoingMessage(req: MessageAction, outgoingMessage: OutgoingMessage): Unit =
    for case (name, data) <- outgoingMessage.files do req.addFile(data, name)
    req.mentionRepliedUser(false)

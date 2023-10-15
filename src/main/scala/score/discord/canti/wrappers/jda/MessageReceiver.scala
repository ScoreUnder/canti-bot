package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.interactions.{Interaction, InteractionHook}
import net.dv8tion.jda.api.utils.messages.{MessageEditBuilder, MessageRequest}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichSnowflake.id
import score.discord.canti.wrappers.jda.RetrievableMessage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import net.dv8tion.jda.api.utils.messages.MessageEditRequest
import net.dv8tion.jda.api.utils.FileUpload
import score.discord.canti.wrappers.jda.MessageReceiver.applyOutgoingMessage
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

trait MessageReceiver:
  def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage]

private class InteractionMessageReceiver(origin: SlashCommandInteraction) extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val req = origin.reply(outgoingMessage.message).nn
    MessageReceiver.applyOutgoingMessage(req, outgoingMessage)
    req.setEphemeral(outgoingMessage.ephemeral)
    req.queueFuture().map(RetrievableMessage(_))(using ExecutionContext.parasitic)

private class InteractionHookMessageReceiver(origin: InteractionHook) extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val req = origin.sendMessage(outgoingMessage.message).nn
    MessageReceiver.applyOutgoingMessage(req, outgoingMessage)
    req.setEphemeral(outgoingMessage.ephemeral)
    req.queueFuture().map(RetrievableMessage(_))(using ExecutionContext.parasitic)

private class MessageReplyMessageReceiver(origin: Message)(using
  messageOwnership: MessageOwnership,
  replyCache: ReplyCache
) extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val req = origin.reply(outgoingMessage.message).nn
    MessageReceiver.applyOutgoingMessage(req, outgoingMessage)
    val future = req.queueFuture()
    for message <- future do
      messageOwnership(message) = origin.getAuthor.nn
      replyCache += origin.id -> message.id
    future.map(RetrievableMessage(_))(using ExecutionContext.parasitic)

private class MessageChannelMessageReceiver(origin: MessageChannel) extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val req = origin.sendMessage(outgoingMessage.message).nn
    MessageReceiver.applyOutgoingMessage(req, outgoingMessage)
    req.queueFuture().map(RetrievableMessage(_))(using ExecutionContext.parasitic)

private class MessageEditMessageReceiver(channel: MessageChannel, messageID: ID[Message])
    extends MessageReceiver:
  override def sendMessage(outgoingMessage: OutgoingMessage): Future[RetrievableMessage] =
    val msgEdit = MessageEditBuilder.fromCreateData(outgoingMessage.message).nn
    val req = channel.editMessageById(messageID.value, msgEdit.build.nn).nn
    MessageReceiver.applyOutgoingMessage(req, outgoingMessage)
    req.queueFuture().map(RetrievableMessage(_))(using ExecutionContext.parasitic)

object MessageReceiver:
  def apply(origin: SlashCommandInteraction): MessageReceiver = InteractionMessageReceiver(origin)
  def apply(origin: InteractionHook): MessageReceiver = InteractionHookMessageReceiver(origin)
  def apply(
    origin: Message
  )(using messageOwnership: MessageOwnership, replyCache: ReplyCache): MessageReceiver =
    MessageReplyMessageReceiver(origin)
  def apply(origin: MessageChannel): MessageReceiver = MessageChannelMessageReceiver(origin)
  def intoEdit(origin: Message): MessageReceiver =
    MessageEditMessageReceiver(origin.getChannel.nn, origin.id)
  def intoEdit(channel: MessageChannel, messageID: ID[Message]): MessageReceiver =
    MessageEditMessageReceiver(channel, messageID)

  object NullReceiver extends MessageReceiver:
    def sendMessage(outgoingMessage: OutgoingMessage) = Future.never

  def applyOutgoingMessage(req: MessageRequest[?], outgoingMessage: OutgoingMessage): Unit =
    req.setFiles(outgoingMessage.files.map { case (name, data) =>
      FileUpload.fromData(data, name).nn
    }*)
    req.mentionRepliedUser(false)

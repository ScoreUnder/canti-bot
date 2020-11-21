package score.discord.generalbot.jdamocks

import java.io.{File, InputStream}
import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import java.util.function.{BiConsumer, BooleanSupplier, Consumer}

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{IMentionable, Message, MessageChannel, MessageEmbed}
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.MessageAction
import net.dv8tion.jda.api.utils.AttachmentOption

class FakeMessageAction(message: Message) extends MessageAction {
  override def queue(success: Consumer[_ >: Message], failure: Consumer[_ >: Throwable]): Unit = success.accept(message)

  override def complete(shouldQueue: Boolean): Message = message

  override def setCheck(checks: BooleanSupplier): MessageAction = ???

  override def getChannel: MessageChannel = ???

  override def isEmpty: Boolean = ???

  override def isEdit: Boolean = ???

  override def apply(message: Message): MessageAction = ???

  override def tts(isTTS: Boolean): MessageAction = ???

  override def reset(): MessageAction = ???

  override def nonce(nonce: String): MessageAction = ???

  override def content(content: String): MessageAction = ???

  override def embed(embed: MessageEmbed): MessageAction = ???

  override def append(csq: CharSequence, start: Int, end: Int): MessageAction = ???

  override def append(c: Char): MessageAction = ???

  override def addFile(data: InputStream, name: String, options: AttachmentOption*): MessageAction = ???

  override def addFile(file: File, name: String, options: AttachmentOption*): MessageAction = ???

  override def clearFiles(): MessageAction = ???

  override def clearFiles(finalizer: BiConsumer[String, InputStream]): MessageAction = ???

  override def clearFiles(finalizer: Consumer[InputStream]): MessageAction = ???

  override def `override`(bool: Boolean): MessageAction = ???

  override def getJDA: JDA = ???

  override def submit(shouldQueue: Boolean): CompletableFuture[Message] = ???

  override def deadline(timestamp: Long): MessageAction = ???

  override def timeout(timeout: Long, unit: TimeUnit): MessageAction = ???

  override def allowedMentions(allowedMentions: util.Collection[Message.MentionType]): MessageAction = ???

  override def mention(mentions: IMentionable*): MessageAction = ???

  override def mentionUsers(userIds: String*): MessageAction = ???

  override def mentionRoles(roleIds: String*): MessageAction = ???

  override def referenceById(messageId: Long): MessageAction = ???

  override def mentionRepliedUser(mention: Boolean): MessageAction = ???
}

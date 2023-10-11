package score.discord.canti.jdamocks

import java.io.{File, InputStream}
import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import java.util.function.{BiConsumer, BooleanSupplier, Consumer}
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{IMentionable, Message, MessageEmbed}
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.interactions.components.LayoutComponent
import net.dv8tion.jda.api.entities.Message.MentionType
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import net.dv8tion.jda.api.utils.FileUpload
import java.util as ju

class FakeMessageAction(message: Message) extends MessageCreateAction:
  override def queue(success: Consumer[? >: Message], failure: Consumer[? >: Throwable]): Unit =
    success.accept(message)

  override def complete(shouldQueue: Boolean): Message = message

  override def setCheck(checks: BooleanSupplier): this.type = ???

  override def getJDA: JDA = ???

  override def submit(shouldQueue: Boolean): CompletableFuture[Message] = ???

  override def deadline(timestamp: Long): this.type = ???

  override def timeout(timeout: Long, unit: TimeUnit): this.type = ???

  override def setAllowedMentions(
    allowedMentions: util.Collection[Message.MentionType]
  ): this.type =
    if allowedMentions.isEmpty then this else ???

  override def mention(mentions: Array[? <: IMentionable]): this.type =
    if mentions.isEmpty then this else ???

  override def mentionUsers(userIds: Array[? <: String]): this.type = ???

  override def mentionRoles(roleIds: Array[? <: String]): this.type = ???

  override def mentionRepliedUser(mention: Boolean): this.type =
    this // Not tracking replies (yet)

  override def failOnInvalidReply(fail: Boolean): this.type = ???

  override def setEmbeds(embeds: util.Collection[? <: MessageEmbed]): this.type = ???

  override def mentionUsers(userIds: ju.Collection[String] | Null): MessageCreateAction | Null = ???

  override def getAllowedMentions(): ju.EnumSet[MentionType] | Null = ???

  override def isMentionRepliedUser(): Boolean = ???

  override def setMessageReference(messageId: String | Null): MessageCreateAction | Null =
    this // Not tracking replies (yet)

  override def setFiles(files: ju.Collection[? <: FileUpload] | Null): MessageCreateAction | Null =
    this // Not tracking files (yet)

  override def getComponents(): ju.List[LayoutComponent] | Null = ???

  override def getMentionedRoles(): ju.Set[String] | Null = ???

  override def setNonce(nonce: String | Null): MessageCreateAction | Null = ???

  override def addComponents(
    components: ju.Collection[? <: LayoutComponent] | Null
  ): MessageCreateAction | Null = ???

  override def addEmbeds(embeds: ju.Collection[? <: MessageEmbed] | Null): MessageCreateAction |
    Null = ???

  override def getContent(): String | Null = ???

  override def setContent(content: String | Null): MessageCreateAction | Null = ???

  override def setSuppressedNotifications(suppressed: Boolean): MessageCreateAction | Null = ???

  override def getMentionedUsers(): ju.Set[String] | Null = ???

  override def setTTS(tts: Boolean): MessageCreateAction | Null = ???

  override def isSuppressEmbeds(): Boolean = ???

  override def addFiles(files: ju.Collection[? <: FileUpload] | Null): MessageCreateAction | Null =
    ???

  override def getAttachments(): ju.List[FileUpload] | Null = ???

  override def addContent(content: String | Null): MessageCreateAction | Null = ???

  override def setStickers(
    stickers: ju.Collection[? <: StickerSnowflake] | Null
  ): MessageCreateAction | Null = ???

  override def getEmbeds(): ju.List[MessageEmbed] | Null = ???

  override def mentionRoles(roleIds: ju.Collection[String] | Null): MessageCreateAction | Null = ???

  override def mention(mentions: ju.Collection[? <: IMentionable] | Null): MessageCreateAction |
    Null = ???

  override def setSuppressEmbeds(suppress: Boolean): MessageCreateAction | Null = ???

  override def setComponents(
    components: ju.Collection[? <: LayoutComponent] | Null
  ): MessageCreateAction | Null = ???
end FakeMessageAction

package score.discord.canti.jdamocks

import java.time.OffsetDateTime
import java.util
import java.util.Formatter

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.{Category, ThreadChannel}
import net.dv8tion.jda.api.entities.channel.unions.{GuildMessageChannelUnion, MessageChannelUnion}
import net.dv8tion.jda.api.entities.messages.MessagePoll
import score.discord.canti.SnowflakeOrdering

import scala.jdk.CollectionConverters.*
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.sticker.StickerItem
import net.dv8tion.jda.api.interactions.components.LayoutComponent
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.{
  AuditableRestAction,
  MessageEditAction,
  ThreadChannelAction
}
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction
import net.dv8tion.jda.api.utils.AttachedFile
import net.dv8tion.jda.api.utils.messages.MessageEditData

class FakeMessage(
  channel: MessageChannel,
  id: Long,
  content: String,
  author: User | Null,
  embeds: util.List[MessageEmbed]
) extends Message
    with SnowflakeOrdering:
  override def getJumpUrl: String = s"https://dummy.jump.url/$id"

  override def getContentDisplay: String = content

  override def getContentRaw: String = content

  override def getContentStripped: String = content

  override def getNonce: String = "dummy nonce"

  override def getChannel: MessageChannelUnion =
    channel.asInstanceOf[MessageChannelUnion]

  override def getChannelType: ChannelType = channel.getType.nn

  override def getChannelIdLong: Long = channel.getIdLong

  override def hasChannel: Boolean = true

  override def isFromType(channelType: ChannelType): Boolean = getChannelType == channelType

  override def isFromGuild: Boolean = channel.getType.nn.isGuild

  override def hasGuild: Boolean = isFromGuild

  override def getGuild: Guild = getGuildChannel.getGuild.nn

  override def getGuildIdLong: Long = getGuild.getIdLong

  override def getGuildChannel: GuildMessageChannelUnion =
    channel.asInstanceOf[GuildMessageChannelUnion]

  override def getCategory: Category = ???

  override def getAuthor: User =
    author.nn // should not be called on a message that isn't "received"

  override def getMember: Member = getGuild.getMember(getAuthor).nn

  override def getEmbeds: util.List[MessageEmbed] = embeds

  override def getAttachments: util.List[Message.Attachment] = Nil.asJava

  override def getComponents: util.List[LayoutComponent] = Nil.asJava

  override def getReactions: util.List[MessageReaction] = Nil.asJava

  override def getJDA: JDA = channel.getJDA.nn

  override def getIdLong: Long = id

  override def getActivity: MessageActivity = ???

  override def getStickers: util.List[StickerItem] = Nil.asJava

  override def getApplicationIdLong(): Long = ???

  override def getPoll(): MessagePoll = ???

  override def endPoll(): AuditableRestAction[Message] = ???

  override def getMessageReference: MessageReference = ???

  override def getMentions: Mentions = ???

  override def isEdited: Boolean = false

  override def getTimeEdited: OffsetDateTime = ???

  override def getApproximatePosition: Int = ???

  override def getInvites: util.List[String] = Nil.asJava

  override def isWebhookMessage: Boolean = false

  override def isTTS: Boolean = false

  override def editMessage(newContent: CharSequence): MessageEditAction = ???

  override def editMessage(data: MessageEditData): MessageEditAction = ???

  override def editMessageEmbeds(
    embeds: util.Collection[? <: MessageEmbed]
  ): MessageEditAction = ???

  override def editMessageComponents(
    components: util.Collection[? <: LayoutComponent]
  ): MessageEditAction = ???

  override def editMessageFormat(format: String, args: Object*): MessageEditAction = ???

  override def editMessageAttachments(
    attachments: util.Collection[? <: AttachedFile]
  ): MessageEditAction = ???

  override def delete(): AuditableRestAction[Void] = ???

  override def isPinned: Boolean = false

  override def pin(): RestAction[Void] = ???

  override def unpin(): RestAction[Void] = ???

  override def addReaction(emoji: Emoji): RestAction[Void] = ???

  override def clearReactions(): RestAction[Void] = ???

  override def clearReactions(emoji: Emoji): RestAction[Void] = ???

  override def removeReaction(emoji: Emoji): RestAction[Void] = ???

  override def removeReaction(emoji: Emoji, user: User): RestAction[Void] = ???

  override def retrieveReactionUsers(emoji: Emoji): ReactionPaginationAction = ???

  override def getReaction(emoji: Emoji): MessageReaction = ???

  override def suppressEmbeds(suppressed: Boolean): AuditableRestAction[Void] = ???

  override def crosspost(): RestAction[Message] = ???

  override def isSuppressedEmbeds: Boolean = false

  override def getFlags: util.EnumSet[Message.MessageFlag] =
    util.EnumSet.noneOf(classOf[Message.MessageFlag]).nn

  override def getFlagsRaw: Long = 0L

  override def isEphemeral: Boolean = false

  override def isSuppressedNotifications: Boolean = false

  override def getStartedThread: ThreadChannel = ???

  override def getType: MessageType = MessageType.DEFAULT

  override def getInteraction: Message.Interaction = ???

  override def createThreadChannel(name: String): ThreadChannelAction = ???

  override def formatTo(formatter: Formatter, flags: Int, width: Int, precision: Int): Unit =
    formatter.format(getJumpUrl)

package score.discord.canti.jdamocks

import java.util
import java.util.Collections

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.*
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import score.discord.canti.wrappers.jda.ID

import scala.jdk.CollectionConverters.*
import scala.util.Try
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import java.util as ju
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel

class FakeTextChannel(guild: FakeGuild, id: Long, name: String)
    extends TextChannel,
      MessageChannelUnion:
  private var cachedMessages = Map.empty[Long, Message]
  private var lastMessage: Long = 0L

  def addMessage(
    content: String,
    author: User | Null,
    embeds: util.List[MessageEmbed] = Nil.asJava
  ): FakeMessage =
    val message = FakeMessage(
      channel = this,
      id = guild.fakeJda.nextId,
      content = content,
      author = author,
      embeds = embeds
    )
    cachedMessages += message.getIdLong -> message
    lastMessage = message.getIdLong
    message

  override def getTopic: String = ???

  override def isNSFW: Boolean = ???

  override def createWebhook(name: String): WebhookAction = ???

  override def deleteMessages(messages: util.Collection[Message]): RestAction[Void] = ???

  override def deleteMessagesByIds(messageIds: util.Collection[String]): RestAction[Void] = ???

  override def deleteWebhookById(id: String): AuditableRestAction[Void] = ???

  override def clearReactionsById(messageId: String): RestAction[Void] = ???

  override def removeReactionById(messageId: String, emoji: Emoji, user: User): RestAction[Void] =
    ???

  override def retrieveMessageById(messageId: String): RestAction[Message] = FakeMessageAction(
    cachedMessages(ID.fromString(messageId).value)
  )

  override def canTalk: Boolean = ???

  override def canTalk(member: Member): Boolean = ???

  override def getGuild: Guild = guild

  override def getParentCategory: Category = ???

  override def getMembers: util.List[Member] = ???

  override def getPosition: Int = ???

  override def getPositionRaw: Int = ???

  override def getPermissionOverrides: util.List[PermissionOverride] = ???

  override def getMemberPermissionOverrides: util.List[PermissionOverride] = ???

  override def sendMessage(msg: MessageCreateData): MessageCreateAction =
    FakeMessageAction(
      addMessage(
        content = msg.getContent.nn,
        author = getJDA.getSelfUser,
        embeds = msg.getEmbeds.nn
      )
    )

  override def getRolePermissionOverrides: util.List[PermissionOverride] = ???

  override def createCopy(): ChannelAction[TextChannel] = createCopy(getGuild)

  override def createCopy(guild: Guild): ChannelAction[TextChannel] = ???

  override def getManager: TextChannelManager = ???

  override def delete(): AuditableRestAction[Void] = ???

  override def createInvite(): InviteAction = ???

  override def getLatestMessageIdLong: Long = lastMessage

  override def getName: String = name

  override def getType: ChannelType = ChannelType.TEXT

  override def getJDA: JDA = guild.getJDA

  override def getAsMention: String = s"<#$id>"

  override def getIdLong: Long = id

  override def getSlowmode: Int = ???

  override def retrieveWebhooks(): RestAction[util.List[Webhook]] = ???

  override def getPermissionOverride(permissionHolder: IPermissionHolder): PermissionOverride = ???

  override def retrieveInvites(): RestAction[util.List[Invite]] = ???

  override def compareTo(o: GuildChannel): Int = ???

  override def clearReactionsById(messageId: String, emote: Emoji): RestAction[Void] = ???

  override def isSynced: Boolean = ???

  override def retrieveArchivedPrivateThreadChannels(): ThreadChannelPaginationAction | Null = ???

  override def getDefaultThreadSlowmode(): Int = ???

  override def retrieveArchivedPublicThreadChannels(): ThreadChannelPaginationAction | Null = ???

  override def sendStickers(
    stickers: ju.Collection[? <: StickerSnowflake] | Null
  ): MessageCreateAction | Null = ???

  override def retrieveArchivedPrivateJoinedThreadChannels(): ThreadChannelPaginationAction | Null =
    ???

  override def getParentCategoryIdLong(): Long = ???

  override def createThreadChannel(name: String | Null, messageId: Long): ThreadChannelAction |
    Null = ???

  override def createThreadChannel(name: String | Null, isPrivate: Boolean): ThreadChannelAction |
    Null = ???

  override def upsertPermissionOverride(
    permissionHolder: IPermissionHolder | Null
  ): PermissionOverrideAction | Null = ???

  override def getPermissionContainer(): IPermissionContainer | Null = ???

  override def asAudioChannel(): AudioChannel | Null = ???

  override def asGuildMessageChannel(): this.type = this

  override def asPrivateChannel(): PrivateChannel | Null = ???

  override def asVoiceChannel(): VoiceChannel | Null = ???

  override def asThreadChannel(): ThreadChannel | Null = ???

  override def asStageChannel(): StageChannel | Null = ???

  override def asTextChannel(): this.type = this

  override def asThreadContainer(): IThreadContainer | Null = ???

  override def asNewsChannel(): NewsChannel | Null = ???
end FakeTextChannel

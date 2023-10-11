package score.discord.canti.jdamocks

import java.util
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.templates.Template
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.managers.{AudioManager, GuildManager}
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.{
  AuditableRestAction, ChannelAction, CommandCreateAction, CommandEditAction,
  CommandListUpdateAction, MemberAction, RoleAction
}
import net.dv8tion.jda.api.requests.restaction.order.{
  CategoryOrderAction, ChannelOrderAction, RoleOrderAction
}
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction
import net.dv8tion.jda.api.utils.cache.{
  MemberCacheView, SnowflakeCacheView, SortedSnowflakeCacheView
}
import net.dv8tion.jda.api.utils.concurrent.Task
import net.dv8tion.jda.api.{JDA, Region}
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.interactions.commands.PrivilegeConfig
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.interactions.DiscordLocale
import java.util.concurrent.TimeUnit
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.requests.restaction.pagination.BanPaginationAction
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion
import net.dv8tion.jda.api.interactions.commands.privileges.IntegrationPrivilege
import net.dv8tion.jda.api.managers.GuildStickerManager
import net.dv8tion.jda.api.entities.automod.AutoModRule
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.requests.restaction.ScheduledEventAction
import net.dv8tion.jda.api.entities.sticker.GuildSticker
import java.util as ju
import net.dv8tion.jda.api.entities.Guild.Ban
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import java.time.temporal.TemporalAccessor
import net.dv8tion.jda.api.managers.AutoModRuleManager
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import java.time.OffsetDateTime
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.managers.GuildWelcomeScreenManager
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel

class FakeGuild(val fakeJda: FakeJda, id: Long) extends Guild:
  var channels = Map.empty[Long, GuildChannel]
  var members = Map.empty[Long, Member]

  def makeTextChannel(name: String): FakeTextChannel =
    val channel = FakeTextChannel(guild = this, id = fakeJda.nextId, name = name)
    channels += channel.getIdLong -> channel
    channel

  def registerMember(user: User): Member =
    val member = FakeGuildMember(guild = this, user = user)
    members += user.getIdLong -> member
    member

  override def getName: String = ???

  override def getIconId: String = ???

  override def getFeatures: util.Set[String] = ???

  override def getSplashId: String = ???

  override def getAfkChannel: VoiceChannel = ???

  override def getSystemChannel: TextChannel = ???

  override def getOwner: Member = ???

  override def getAfkTimeout: Guild.Timeout = ???

  override def isMember(user: UserSnowflake): Boolean = ???

  override def getSelfMember: Member = ???

  override def getMember(user: UserSnowflake): Member | Null = members.get(user.getIdLong).orNull

  override def getMemberCache: MemberCacheView = ???

  override def getCategoryCache: SortedSnowflakeCacheView[Category] = ???

  override def getTextChannelCache: SortedSnowflakeCacheView[TextChannel] =
    ScalaSnowflakeCacheView[GuildChannel, TextChannel](
      channels.collect { case (id, c: TextChannel) =>
        id -> c
      },
      _.getName.nn
    )

  override def getVoiceChannelCache: SortedSnowflakeCacheView[VoiceChannel] = ???

  override def getRoleCache: SortedSnowflakeCacheView[Role] = ???

  override def getPublicRole: Role = ???

  override def getDefaultChannel: DefaultGuildChannelUnion = ???

  override def getManager: GuildManager = ???

  override def leave(): RestAction[Void] = ???

  override def delete(): RestAction[Void] = ???

  override def delete(mfaCode: String): RestAction[Void] = ???

  override def getAudioManager: AudioManager = ???

  override def getJDA: JDA = fakeJda

  override def getVoiceStates: util.List[GuildVoiceState] = ???

  override def getVerificationLevel: Guild.VerificationLevel = ???

  override def getDefaultNotificationLevel: Guild.NotificationLevel = ???

  override def getRequiredMFALevel: Guild.MFALevel = ???

  override def getExplicitContentLevel: Guild.ExplicitContentLevel = ???

  override def getIdLong: Long = id

  override def retrieveRegions(includeDeprecated: Boolean): RestAction[util.EnumSet[Region]] = ???

  override def getVanityCode: String = ???

  override def getDescription: String = ???

  override def getBannerId: String = ???

  override def getBoostTier: Guild.BoostTier = ???

  override def getBoostCount: Int = ???

  override def getBoosters: util.List[Member] = ???

  override def getMaxMembers: Int = ???

  override def getMaxPresences: Int = ???

  override def getOwnerIdLong: Long = ???

  override def getChannels(includeHidden: Boolean): util.List[GuildChannel] = ???

  override def retrieveBanList(): BanPaginationAction = ???

  override def retrievePrunableMemberCount(days: Int): RestAction[Integer] = ???

  override def retrieveAuditLogs(): AuditLogPaginationAction = ???

  override def retrieveInvites(): RestAction[util.List[Invite]] = ???

  override def retrieveWebhooks(): RestAction[util.List[Webhook]] = ???

  override def moveVoiceMember(member: Member, voiceChannel: AudioChannel): RestAction[Void] = ???

  override def modifyNickname(member: Member, nickname: String): AuditableRestAction[Void] = ???

  override def kick(member: UserSnowflake, reason: String): AuditableRestAction[Void] = ???

  override def ban(user: UserSnowflake, time: Int, unit: TimeUnit): AuditableRestAction[Void] = ???

  override def unban(userId: UserSnowflake): AuditableRestAction[Void] = ???

  override def deafen(member: UserSnowflake, deafen: Boolean): AuditableRestAction[Void] = ???

  override def mute(member: UserSnowflake, mute: Boolean): AuditableRestAction[Void] = ???

  override def addRoleToMember(member: UserSnowflake, role: Role): AuditableRestAction[Void] = ???

  override def removeRoleFromMember(member: UserSnowflake, role: Role): AuditableRestAction[Void] =
    ???

  override def modifyMemberRoles(
    member: Member,
    rolesToAdd: util.Collection[Role],
    rolesToRemove: util.Collection[Role]
  ): AuditableRestAction[Void] = ???

  override def modifyMemberRoles(
    member: Member,
    roles: util.Collection[Role]
  ): AuditableRestAction[Void] = ???

  override def transferOwnership(newOwner: Member): AuditableRestAction[Void] = ???

  override def createCategory(name: String): ChannelAction[Category] = ???

  override def createRole(): RoleAction = ???

  override def modifyCategoryPositions(): ChannelOrderAction = ???

  override def modifyTextChannelPositions(): ChannelOrderAction = ???

  override def modifyVoiceChannelPositions(): ChannelOrderAction = ???

  override def modifyTextChannelPositions(category: Category): CategoryOrderAction = ???

  override def modifyVoiceChannelPositions(category: Category): CategoryOrderAction = ???

  override def modifyRolePositions(useAscendingOrder: Boolean): RoleOrderAction = ???

  override def isLoaded: Boolean = ???

  override def getMemberCount: Int = ???

  override def pruneMemberCache(): Unit = ???

  override def unloadMember(userId: Long): Boolean = ???

  override def retrieveMetaData(): RestAction[Guild.MetaData] = ???

  override def retrieveMemberById(id: Long): CacheRestAction[Member] = ???

  override def retrieveMembersByIds(
    includePresence: Boolean,
    ids: Array[? <: Long]
  ): Task[util.List[Member]] = ???

  override def retrieveMembersByPrefix(prefix: String, limit: Int): Task[util.List[Member]] = ???

  override def getLocale: DiscordLocale = ???

  override def loadMembers(callback: Consumer[Member]): Task[Void] = ???

  override def prune(
    days: Int,
    wait: Boolean,
    roles: Array[? <: Role]
  ): AuditableRestAction[Integer] = ???

  override def createTextChannel(name: String, parent: Category): ChannelAction[TextChannel] = ???

  override def createVoiceChannel(name: String, parent: Category): ChannelAction[VoiceChannel] = ???

  override def getCommunityUpdatesChannel(): TextChannel = ???

  override def getRulesChannel(): TextChannel = ???

  override def retrieveVanityInvite(): RestAction[VanityInvite] = ???

  override def retrieveCommands(): RestAction[util.List[Command]] = ???

  override def retrieveCommandById(id: String): RestAction[Command] = ???

  override def upsertCommand(command: CommandData): CommandCreateAction = ???

  override def updateCommands(): CommandListUpdateAction = ???

  override def editCommandById(id: String): CommandEditAction = ???

  override def deleteCommandById(commandId: String): RestAction[Void] = ???

  override def createTemplate(name: String, description: String): RestAction[Template] = ???

  override def retrieveTemplates(): RestAction[util.List[Template]] = ???

  override def cancelRequestToSpeak(): Task[Void] = ???

  override def createStageChannel(name: String, category: Category): ChannelAction[StageChannel] =
    ???

  override def requestToSpeak(): Task[Void] = ???

  override def getNSFWLevel(): Guild.NSFWLevel = ???

  override def removeTimeout(user: UserSnowflake | Null): AuditableRestAction[Void] | Null = ???

  override def getThreadChannelCache(): SortedSnowflakeCacheView[ThreadChannel] | Null = ???

  override def createEmoji(
    name: String | Null,
    icon: Icon | Null,
    roles: (Role | Null)*
  ): AuditableRestAction[RichCustomEmoji] | Null = ???

  override def modifyAutoModRuleById(id: String | Null): AutoModRuleManager | Null = ???

  override def retrieveStickers(): RestAction[ju.List[GuildSticker]] | Null = ???

  override def addMember(accessToken: String | Null, user: UserSnowflake | Null): MemberAction |
    Null = ???

  override def createScheduledEvent(
    name: String | Null,
    channel: GuildChannel | Null,
    startTime: OffsetDateTime | Null
  ): ScheduledEventAction | Null = ???

  override def createScheduledEvent(
    name: String | Null,
    location: String | Null,
    startTime: OffsetDateTime | Null,
    endTime: OffsetDateTime | Null
  ): ScheduledEventAction | Null = ???

  override def getScheduledEventCache(): SortedSnowflakeCacheView[ScheduledEvent] | Null = ???

  override def retrieveEmojis(): RestAction[ju.List[RichCustomEmoji]] | Null = ???

  override def timeoutUntil(
    user: UserSnowflake | Null,
    temporal: TemporalAccessor | Null
  ): AuditableRestAction[Void] | Null = ???

  override def retrieveAutoModRules(): RestAction[ju.List[AutoModRule]] | Null = ???

  override def kick(user: UserSnowflake | Null): AuditableRestAction[Void] | Null = ???

  override def deleteAutoModRuleById(id: String | Null): AuditableRestAction[Void] | Null = ???

  override def retrieveActiveThreads(): RestAction[ju.List[ThreadChannel]] | Null = ???

  override def retrieveIntegrationPrivilegesById(
    targetId: String | Null
  ): RestAction[ju.List[IntegrationPrivilege]] | Null = ???

  override def getForumChannelCache(): SortedSnowflakeCacheView[ForumChannel] | Null = ???

  override def getMediaChannelCache(): SnowflakeCacheView[MediaChannel] | Null = ???

  override def createForumChannel(
    name: String | Null,
    parent: Category | Null
  ): ChannelAction[ForumChannel] | Null = ???

  override def retrieveCommandPrivileges(): RestAction[PrivilegeConfig] | Null = ???

  override def createMediaChannel(
    name: String | Null,
    parent: Category | Null
  ): ChannelAction[MediaChannel] | Null = ???

  override def retrieveAutoModRuleById(id: String | Null): RestAction[AutoModRule] | Null = ???

  override def retrieveWelcomeScreen(): RestAction[GuildWelcomeScreen] | Null = ???

  override def getNewsChannelCache(): SortedSnowflakeCacheView[NewsChannel] | Null = ???

  override def retrieveBan(user: UserSnowflake | Null): RestAction[Ban] | Null = ???

  override def retrieveScheduledEventById(id: String | Null): CacheRestAction[ScheduledEvent] |
    Null = ???

  override def createSticker(
    name: String | Null,
    description: String | Null,
    file: FileUpload | Null,
    tags: ju.Collection[String] | Null
  ): AuditableRestAction[GuildSticker] | Null = ???

  override def createNewsChannel(
    name: String | Null,
    parent: Category | Null
  ): ChannelAction[NewsChannel] | Null = ???

  override def retrieveCommands(withLocalizations: Boolean): RestAction[ju.List[Command]] | Null =
    ???

  override def retrieveSticker(sticker: StickerSnowflake | Null): RestAction[GuildSticker] | Null =
    ???

  override def getEmojiCache(): SnowflakeCacheView[RichCustomEmoji] | Null = ???

  override def retrieveEmojiById(id: String | Null): RestAction[RichCustomEmoji] | Null = ???

  override def editSticker(sticker: StickerSnowflake | Null): GuildStickerManager | Null = ???

  override def getStickerCache(): SnowflakeCacheView[GuildSticker] | Null = ???

  override def modifyWelcomeScreen(): GuildWelcomeScreenManager | Null = ???

  override def createAutoModRule(data: AutoModRuleData | Null): AuditableRestAction[AutoModRule] |
    Null = ???

  override def isBoostProgressBarEnabled(): Boolean = ???

  override def deleteSticker(id: StickerSnowflake | Null): AuditableRestAction[Void] | Null = ???

  override def getStageChannelCache(): SortedSnowflakeCacheView[StageChannel] | Null = ???
end FakeGuild

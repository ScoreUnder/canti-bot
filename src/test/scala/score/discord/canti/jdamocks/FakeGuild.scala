package score.discord.canti.jdamocks

import java.util
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.templates.Template
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege
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

  override def getRegionRaw: String = ???

  override def isMember(user: User): Boolean = ???

  override def getSelfMember: Member = ???

  override def getMember(user: User): Member | Null = members.get(user.getIdLong).orNull

  override def getMemberCache: MemberCacheView = ???

  override def getCategoryCache: SortedSnowflakeCacheView[Category] = ???

  override def getTextChannelCache: SortedSnowflakeCacheView[TextChannel] =
    ScalaSnowflakeCacheView[GuildChannel, TextChannel](
      channels.collect { case (id, c: TextChannel) =>
        id -> c
      },
      _.getName
    )

  override def getVoiceChannelCache: SortedSnowflakeCacheView[VoiceChannel] = ???

  override def getRoleCache: SortedSnowflakeCacheView[Role] = ???

  override def getEmoteCache: SnowflakeCacheView[Emote] = ???

  override def getPublicRole: Role = ???

  override def getDefaultChannel: TextChannel = ???

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

  override def checkVerification(): Boolean = ???

  override def isAvailable: Boolean = ???

  override def getIdLong: Long = id

  override def retrieveRegions(includeDeprecated: Boolean): RestAction[util.EnumSet[Region]] = ???

  override def addMember(accessToken: String, userId: String): MemberAction = ???

  override def retrieveVanityUrl(): RestAction[String] = ???

  override def getVanityCode: String = ???

  override def getDescription: String = ???

  override def getBannerId: String = ???

  override def getBoostTier: Guild.BoostTier = ???

  override def getBoostCount: Int = ???

  override def getBoosters: util.List[Member] = ???

  override def getMaxMembers: Int = ???

  override def getMaxPresences: Int = ???

  override def getOwnerIdLong: Long = ???

  override def getStoreChannelCache: SortedSnowflakeCacheView[StoreChannel] = ???

  override def getChannels(includeHidden: Boolean): util.List[GuildChannel] = ???

  override def retrieveEmotes(): RestAction[util.List[ListedEmote]] = ???

  override def retrieveEmoteById(id: String): RestAction[ListedEmote] = ???

  override def retrieveBanList(): RestAction[util.List[Guild.Ban]] = ???

  override def retrieveBanById(userId: String): RestAction[Guild.Ban] = ???

  override def retrievePrunableMemberCount(days: Int): RestAction[Integer] = ???

  override def retrieveAuditLogs(): AuditLogPaginationAction = ???

  override def retrieveInvites(): RestAction[util.List[Invite]] = ???

  override def retrieveWebhooks(): RestAction[util.List[Webhook]] = ???

  override def moveVoiceMember(member: Member, voiceChannel: VoiceChannel): RestAction[Void] = ???

  override def modifyNickname(member: Member, nickname: String): AuditableRestAction[Void] = ???

  override def kick(member: Member, reason: String): AuditableRestAction[Void] = ???

  override def ban(user: User, delDays: Int, reason: String): AuditableRestAction[Void] = ???

  override def ban(userId: String, delDays: Int, reason: String): AuditableRestAction[Void] = ???

  override def unban(userId: String): AuditableRestAction[Void] = ???

  override def deafen(member: Member, deafen: Boolean): AuditableRestAction[Void] = ???

  override def mute(member: Member, mute: Boolean): AuditableRestAction[Void] = ???

  override def addRoleToMember(member: Member, role: Role): AuditableRestAction[Void] = ???

  override def removeRoleFromMember(member: Member, role: Role): AuditableRestAction[Void] = ???

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

  override def createEmote(name: String, icon: Icon, roles: Role*): AuditableRestAction[Emote] = ???

  override def modifyCategoryPositions(): ChannelOrderAction = ???

  override def modifyTextChannelPositions(): ChannelOrderAction = ???

  override def modifyVoiceChannelPositions(): ChannelOrderAction = ???

  override def modifyTextChannelPositions(category: Category): CategoryOrderAction = ???

  override def modifyVoiceChannelPositions(category: Category): CategoryOrderAction = ???

  override def modifyRolePositions(useAscendingOrder: Boolean): RoleOrderAction = ???

  override def isLoaded: Boolean = ???

  override def getMemberCount: Int = ???

  override def retrieveMembers(): CompletableFuture[Void] = ???

  override def kick(userId: String, reason: String): AuditableRestAction[Void] = ???

  override def pruneMemberCache(): Unit = ???

  override def unloadMember(userId: Long): Boolean = ???

  override def retrieveMetaData(): RestAction[Guild.MetaData] = ???

  override def retrieveMemberById(id: Long, update: Boolean): RestAction[Member] = ???

  override def retrieveMembersByIds(
    includePresence: Boolean,
    ids: Array[? <: Long]
  ): Task[util.List[Member]] = ???

  override def retrieveMembersByPrefix(prefix: String, limit: Int): Task[util.List[Member]] = ???

  override def getLocale: Locale = ???

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

  override def retrieveCommandPrivilegesById(
    commandId: String
  ): RestAction[util.List[CommandPrivilege]] = ???

  override def retrieveCommandPrivileges()
    : RestAction[util.Map[String, util.List[CommandPrivilege]]] = ???

  override def updateCommandPrivilegesById(
    id: String,
    privileges: util.Collection[? <: CommandPrivilege]
  ): RestAction[util.List[CommandPrivilege]] = ???

  override def updateCommandPrivileges(
    privileges: util.Map[String, util.Collection[? <: CommandPrivilege]]
  ): RestAction[util.Map[String, util.List[CommandPrivilege]]] = ???

  override def createTemplate(name: String, description: String): RestAction[Template] = ???

  override def retrieveTemplates(): RestAction[util.List[Template]] = ???

  override def cancelRequestToSpeak(): Task[Void] = ???

  override def createStageChannel(name: String, category: Category): ChannelAction[StageChannel] =
    ???

  override def requestToSpeak(): Task[Void] = ???
end FakeGuild

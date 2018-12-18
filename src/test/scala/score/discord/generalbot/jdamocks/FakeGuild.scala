package score.discord.generalbot.jdamocks

import java.util

import net.dv8tion.jda.client.requests.restaction.pagination.MentionPaginationAction
import net.dv8tion.jda.core.{JDA, Region}
import net.dv8tion.jda.core.entities._
import net.dv8tion.jda.core.managers.{AudioManager, GuildController, GuildManager, GuildManagerUpdatable}
import net.dv8tion.jda.core.requests.RestAction
import net.dv8tion.jda.core.requests.restaction.pagination.AuditLogPaginationAction
import net.dv8tion.jda.core.utils.cache.{MemberCacheView, SnowflakeCacheView}

class FakeGuild(val fakeJda: FakeJda, id: Long) extends Guild {
  var channels = Map.empty[Long, Channel]
  var members = Map.empty[Long, Member]

  def makeTextChannel(name: String): FakeTextChannel = {
    val channel = new FakeTextChannel(guild = this, id = fakeJda.nextId, name = name)
    channels += channel.getIdLong -> channel
    channel
  }

  def registerMember(user: User): Member = {
    val member = new FakeGuildMember(guild = this, user = user)
    members += user.getIdLong -> member
    member
  }

  override def retrieveRegions(): RestAction[util.EnumSet[Region]] = ???

  override def getName: String = ???

  override def getIconId: String = ???

  override def getIconUrl: String = ???

  override def getFeatures: util.Set[String] = ???

  override def getSplashId: String = ???

  override def getSplashUrl: String = ???

  override def getVanityUrl: RestAction[String] = ???

  override def getAfkChannel: VoiceChannel = ???

  override def getSystemChannel: TextChannel = ???

  override def getOwner: Member = ???

  override def getAfkTimeout: Guild.Timeout = ???

  override def getRegionRaw: String = ???

  override def isMember(user: User): Boolean = ???

  override def getSelfMember: Member = ???

  override def getMember(user: User): Member = members.get(user.getIdLong).orNull

  override def getMemberCache: MemberCacheView = ???

  override def getCategoryCache: SnowflakeCacheView[Category] = ???

  override def getTextChannelCache: SnowflakeCacheView[TextChannel] = new ScalaSnowflakeCacheView(channels.collect {
    case (id, c: TextChannel) => id -> c
  }, _.getName)

  override def getVoiceChannelCache: SnowflakeCacheView[VoiceChannel] = ???

  override def getRoleCache: SnowflakeCacheView[Role] = ???

  override def getEmoteCache: SnowflakeCacheView[Emote] = ???

  override def getBanList: RestAction[util.List[Guild.Ban]] = ???

  override def getPrunableMemberCount(days: Int): RestAction[Integer] = ???

  override def getPublicRole: Role = ???

  override def getDefaultChannel: TextChannel = ???

  override def getManager: GuildManager = ???

  override def getManagerUpdatable: GuildManagerUpdatable = ???

  override def getController: GuildController = ???

  override def getRecentMentions: MentionPaginationAction = ???

  override def getAuditLogs: AuditLogPaginationAction = ???

  override def leave(): RestAction[Void] = ???

  override def delete(): RestAction[Void] = ???

  override def delete(mfaCode: String): RestAction[Void] = ???

  override def getAudioManager: AudioManager = ???

  override def getJDA: JDA = fakeJda

  override def getInvites: RestAction[util.List[Invite]] = ???

  override def getWebhooks: RestAction[util.List[Webhook]] = ???

  override def getVoiceStates: util.List[GuildVoiceState] = ???

  override def getVerificationLevel: Guild.VerificationLevel = ???

  override def getDefaultNotificationLevel: Guild.NotificationLevel = ???

  override def getRequiredMFALevel: Guild.MFALevel = ???

  override def getExplicitContentLevel: Guild.ExplicitContentLevel = ???

  override def checkVerification(): Boolean = ???

  override def isAvailable: Boolean = ???

  override def getIdLong: Long = id
}

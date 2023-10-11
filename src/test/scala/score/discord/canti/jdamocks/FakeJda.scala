package score.discord.canti.jdamocks

import java.util
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.hooks.IEventManager
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.managers.{AudioManager, DirectAudioController, Presence}
import net.dv8tion.jda.api.requests.{GatewayIntent, RestAction}
import net.dv8tion.jda.api.requests.restaction.{
  CommandCreateAction, CommandEditAction, CommandListUpdateAction, GuildAction
}
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.cache.{CacheFlag, CacheView, SnowflakeCacheView}
import net.dv8tion.jda.api.{AccountType, JDA, Permission}
import okhttp3.OkHttpClient

import scala.jdk.CollectionConverters.*
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.sticker.StickerPack
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import java.util as ju
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel
import net.dv8tion.jda.api.entities.sticker.StickerUnion
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import java.util.concurrent.TimeUnit
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import net.dv8tion.jda.api.JDA.Status
import scala.annotation.varargs
import net.dv8tion.jda.api.managers.AccountManager

class FakeJda extends JDA:
  private var guilds = Map.empty[Long, Guild]
  private var _nextId: Long = 123456789900L

  def nextId: Long =
    _nextId += 1
    _nextId

  def makeGuild(): FakeGuild =
    val guild = FakeGuild(this, nextId)
    guilds += guild.getIdLong -> guild
    guild

  override def getStatus: JDA.Status = ???

  override def setEventManager(manager: IEventManager): Unit = ???

  override def addEventListener(listeners: Array[? <: Object | Null]): Unit = ???

  override def removeEventListener(listeners: Array[? <: Object | Null]): Unit = ???

  override def getRegisteredListeners: util.List[AnyRef] = ???

  override def createGuild(name: String): GuildAction = ???

  override def getAudioManagerCache: CacheView[AudioManager] = ???

  override def getUserCache: SnowflakeCacheView[User] = ???

  override def getMutualGuilds(users: Array[? <: User]): util.List[Guild] = ???

  override def getMutualGuilds(users: util.Collection[User]): util.List[Guild] = ???

  override def getGuildCache: SnowflakeCacheView[Guild] = ???

  override def getRoleCache: SnowflakeCacheView[Role] = ???

  override def getCategoryCache: SnowflakeCacheView[Category] = ???

  override def getTextChannelCache: SnowflakeCacheView[TextChannel] =
    ScalaSnowflakeCacheView[GuildChannel, TextChannel](
      guilds.values
        .flatMap(_.getTextChannels.nn.asScala)
        .groupBy(_.getIdLong)
        .view
        .mapValues(_.head)
        .toMap,
      _.getName.nn
    )

  override def getVoiceChannelCache: SnowflakeCacheView[VoiceChannel] = ???

  override def getPrivateChannelCache: SnowflakeCacheView[PrivateChannel] = ???

  override def getEmojiCache: SnowflakeCacheView[RichCustomEmoji] = ???

  override val getSelfUser: SelfUser = new FakeUser(this, "Self-user", -123123L) with SelfUser:
    override def getAllowedFileSize(): Long = ???

    override def getApplicationIdLong(): Long = ???

    override def isVerified(): Boolean = ???

    override def isMfaEnabled(): Boolean = ???

    override def getManager(): AccountManager | Null = ???

  override def getPresence: Presence = ???

  override def getShardInfo: JDA.ShardInfo = ???

  override def getToken: String = ???

  override def getResponseTotal: Long = ???

  override def getMaxReconnectDelay: Int = ???

  override def setAutoReconnect(reconnect: Boolean): Unit = ???

  override def setRequestTimeoutRetry(retryOnTimeout: Boolean): Unit = ???

  override def isAutoReconnect: Boolean = ???

  override def isBulkDeleteSplittingEnabled: Boolean = ???

  override def shutdown(): Unit = ???

  override def shutdownNow(): Unit = ???

  override def getGatewayPing: Long = ???

  override def awaitStatus(status: JDA.Status): JDA = ???

  override def getRateLimitPool: ScheduledExecutorService = ???

  override def getGatewayPool: ScheduledExecutorService = ???

  override def getCallbackPool: ExecutorService = ???

  override def getHttpClient: OkHttpClient = ???

  override def getDirectAudioController: DirectAudioController = ???

  override def getEventManager: IEventManager = ???

  override def retrieveApplicationInfo(): RestAction[ApplicationInfo] = ???

  override def getInviteUrl(permissions: Array[? <: Permission]): String =
    s"https://test.invalid/invite?perms=${permissions.map(_.name).mkString(",")}"

  override def getInviteUrl(permissions: util.Collection[Permission]): String = getInviteUrl(
    permissions.asScala.toArray
  )

  override def getShardManager: ShardManager = ???

  override def retrieveWebhookById(webhookId: String): RestAction[Webhook] = ???

  override def awaitStatus(status: JDA.Status, failOn: Array[? <: JDA.Status]): JDA = ???

  override def getUnavailableGuilds: util.Set[String] = ???

  override def getGatewayIntents: util.EnumSet[GatewayIntent] = ???

  override def unloadUser(userId: Long): Boolean = ???

  override def cancelRequests(): Int = ???

  override def isUnavailable(guildId: Long): Boolean = ???

  override def getCacheFlags: util.EnumSet[CacheFlag] = ???

  override def retrieveCommands(): RestAction[util.List[Command]] = ???

  override def retrieveCommandById(id: String): RestAction[Command] = ???

  override def upsertCommand(command: CommandData): CommandCreateAction = ???

  override def updateCommands(): CommandListUpdateAction = ???

  override def editCommandById(id: String): CommandEditAction = ???

  override def deleteCommandById(commandId: String): RestAction[Void] = ???

  override def setRequiredScopes(scopes: util.Collection[String]): JDA = ???

  override def createGuildFromTemplate(code: String, name: String, icon: Icon): RestAction[Void] =
    ???

  override def getThreadChannelCache(): SnowflakeCacheView[ThreadChannel] = ???

  override def getScheduledEventCache(): SnowflakeCacheView[ScheduledEvent] = ???

  override def getNewsChannelCache(): SnowflakeCacheView[NewsChannel] = ???

  override def getStageChannelCache(): SnowflakeCacheView[StageChannel] = ???

  override def retrieveUserById(id: Long): CacheRestAction[User] = ???

  override def retrieveRoleConnectionMetadata(): RestAction[ju.List[RoleConnectionMetadata]] = ???

  override def awaitShutdown(duration: Long, unit: TimeUnit | Null): Boolean = ???

  override def getForumChannelCache(): SnowflakeCacheView[ForumChannel] = ???

  override def getMediaChannelCache(): SnowflakeCacheView[MediaChannel] = ???

  override def openPrivateChannelById(userId: Long): CacheRestAction[PrivateChannel] = ???

  override def retrieveSticker(sticker: StickerSnowflake | Null): RestAction[StickerUnion] = ???

  override def updateRoleConnectionMetadata(
    records: ju.Collection[? <: RoleConnectionMetadata] | Null
  ): RestAction[ju.List[RoleConnectionMetadata]] = ???

  override def retrieveCommands(withLocalizations: Boolean): RestAction[ju.List[Command]] = ???

  override def retrieveNitroStickerPacks(): RestAction[ju.List[StickerPack]] = ???

end FakeJda

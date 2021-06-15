package score.discord.canti.jdamocks

import java.util
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import net.dv8tion.jda.api.entities._
import net.dv8tion.jda.api.hooks.IEventManager
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.managers.{AudioManager, DirectAudioController, Presence}
import net.dv8tion.jda.api.requests.{GatewayIntent, RestAction}
import net.dv8tion.jda.api.requests.restaction.{CommandCreateAction, CommandEditAction, CommandListUpdateAction, GuildAction}
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.cache.{CacheFlag, CacheView, SnowflakeCacheView}
import net.dv8tion.jda.api.{AccountType, JDA, Permission}
import okhttp3.OkHttpClient

import scala.jdk.CollectionConverters._

class FakeJda extends JDA {
  private var guilds = Map.empty[Long, Guild]
  private var _nextId: Long = 123456789900L

  def nextId: Long = {
    _nextId += 1
    _nextId
  }

  def makeGuild(): FakeGuild = {
    val guild = new FakeGuild(this, nextId)
    guilds += guild.getIdLong -> guild
    guild
  }

  override def getStatus: JDA.Status = ???

  override def setEventManager(manager: IEventManager): Unit = ???

  override def addEventListener(listeners: Object*): Unit = ???

  override def removeEventListener(listeners: Object*): Unit = ???

  override def getRegisteredListeners: util.List[AnyRef] = ???

  override def createGuild(name: String): GuildAction = ???

  override def getAudioManagerCache: CacheView[AudioManager] = ???

  override def getUserCache: SnowflakeCacheView[User] = ???

  override def getMutualGuilds(users: User*): util.List[Guild] = ???

  override def getMutualGuilds(users: util.Collection[User]): util.List[Guild] = ???

  override def retrieveUserById(id: String): RestAction[User] = ???

  override def retrieveUserById(id: Long): RestAction[User] = ???

  override def getGuildCache: SnowflakeCacheView[Guild] = ???

  override def getRoleCache: SnowflakeCacheView[Role] = ???

  override def getCategoryCache: SnowflakeCacheView[Category] = ???

  override def getTextChannelCache: SnowflakeCacheView[TextChannel] =
    new ScalaSnowflakeCacheView[GuildChannel, TextChannel](
      guilds.values.flatMap(_.getTextChannels.asScala).groupBy(_.getIdLong).view.mapValues(_.head).toMap,
      _.getName)

  override def getVoiceChannelCache: SnowflakeCacheView[VoiceChannel] = ???

  override def getPrivateChannelCache: SnowflakeCacheView[PrivateChannel] = ???

  override def getEmoteCache: SnowflakeCacheView[Emote] = ???

  override def getSelfUser: SelfUser = ???

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

  override def getAccountType: AccountType = ???

  override def getGatewayPing: Long = ???

  override def awaitStatus(status: JDA.Status): JDA = ???

  override def getRateLimitPool: ScheduledExecutorService = ???

  override def getGatewayPool: ScheduledExecutorService = ???

  override def getCallbackPool: ExecutorService = ???

  override def getHttpClient: OkHttpClient = ???

  override def getDirectAudioController: DirectAudioController = ???

  override def getStoreChannelCache: SnowflakeCacheView[StoreChannel] = ???

  override def getEventManager: IEventManager = ???

  override def retrieveApplicationInfo(): RestAction[ApplicationInfo] = ???

  override def getInviteUrl(permissions: Permission*): String = s"https://test.invalid/invite?perms=${permissions.map(_.name).mkString(",")}"

  override def getInviteUrl(permissions: util.Collection[Permission]): String = getInviteUrl(permissions.asScala.toSeq: _*)

  override def getShardManager: ShardManager = ???

  override def retrieveWebhookById(webhookId: String): RestAction[Webhook] = ???

  override def awaitStatus(status: JDA.Status, failOn: JDA.Status*): JDA = ???

  override def getUnavailableGuilds: util.Set[String] = ???

  override def getGatewayIntents: util.EnumSet[GatewayIntent] = ???

  override def unloadUser(userId: Long): Boolean = ???

  override def cancelRequests(): Int = ???

  override def retrieveUserById(id: Long, update: Boolean): RestAction[User] = ???

  override def isUnavailable(guildId: Long): Boolean = ???

  override def openPrivateChannelById(userId: Long): RestAction[PrivateChannel] = ???

  override def getCacheFlags: util.EnumSet[CacheFlag] = ???

  override def retrieveCommands(): RestAction[util.List[Command]] = ???

  override def retrieveCommandById(id: String): RestAction[Command] = ???

  override def upsertCommand(command: CommandData): CommandCreateAction = ???

  override def updateCommands(): CommandListUpdateAction = ???

  override def editCommandById(id: String): CommandEditAction = ???

  override def deleteCommandById(commandId: String): RestAction[Void] = ???

  override def setRequiredScopes(scopes: util.Collection[String]): JDA = ???

  override def createGuildFromTemplate(code: String, name: String, icon: Icon): RestAction[Void] = ???
}

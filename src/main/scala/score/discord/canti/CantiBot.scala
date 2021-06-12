package score.discord.canti

import com.typesafe.config.ConfigFactory
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.{JDA, JDABuilder}
import score.discord.canti.collections.CacheCoordinator._
import score.discord.canti.collections._
import score.discord.canti.command._
import score.discord.canti.command.slash.RegisterGuildSlashCommandsCommand
import score.discord.canti.functionality._
import score.discord.canti.functionality.ownership.{DeleteOwnedMessages, MessageOwnership}
import score.discord.canti.functionality.voicekick.VoiceKick
import score.discord.canti.wrappers.Scheduler
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.io.{File, IOException}
import java.net.URLClassLoader
import java.util
import java.util.concurrent.{Executors, ScheduledExecutorService}
import scala.concurrent.duration._
import scala.language.postfixOps

object CantiBot extends App {
  new CantiBot().start()
}

class CantiBot {
  private var discord: Option[JDA] = None
  private var executor: ScheduledExecutorService = _

  def start(): Unit = {
    discord match {
      case None =>
        val rawConfig = ConfigFactory.load(URLClassLoader.newInstance(Array(
          new File(".").toURI.toURL
        )))
        val config = Config.load(rawConfig)
        val bot = JDABuilder.create(config.token, {
          import GatewayIntent._
          util.Arrays.asList(
            GUILD_EMOJIS, /* &find */
            GUILD_MEMBERS, /* &find, voice roles, probably other things too */
            GUILD_MESSAGE_REACTIONS, /* Voice kick, &help, &spoiler, delete owned messages */
            GUILD_MESSAGES, /* commands in general */
            GUILD_VOICE_STATES, /* Voice kick, private voice chats */
            DIRECT_MESSAGES, /* Same as GUILD_MESSAGES */
            DIRECT_MESSAGE_REACTIONS, /* Same as GUILD_MESSAGE_REACTIONS */
          )
        })
          .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS)
        val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("database", rawConfig)
        executor = Executors.newScheduledThreadPool(Runtime.getRuntime.availableProcessors)
        implicit val scheduler = new Scheduler(executor)
        implicit val messageOwnership = new MessageOwnership(new UserByMessage(dbConfig, "message_ownership") withCache LruCache.empty(20000))
        implicit val messageCache = new MessageCache
        implicit val replyCache = new ReplyCache
        val userCreatedChannels = new UserByVoiceChannel(dbConfig, "user_created_channels") withCache LruCache.empty(2000)

        val eventWaiter = new EventWaiter
        val commands = new Commands
        val quoteCommand = new QuoteCommand
        val findCommand = new FindCommand
        val conversations = new Conversations
        val voiceKick = new VoiceKick(userCreatedChannels, new VoiceBanExpiryTable(dbConfig, "voice_ban_expiries"))
        val privateVoiceChats = new PrivateVoiceChats(userCreatedChannels, new ChannelByGuild(dbConfig, "voice_default_category") withCache LruCache.empty(2000), commands, eventWaiter)
        val slashCommands = new SlashCommands(privateVoiceChats.allSlashCommands: _*)
        bot.addEventListeners(
          commands,
          slashCommands,
          new VoiceRoles(new RoleByGuild(dbConfig, "voice_active_role") withCache LruCache.empty(2000), commands),
          privateVoiceChats,
          new DeleteOwnedMessages,
          conversations,
          new Spoilers(new StringByMessage(dbConfig, "spoilers_by_message") withCache LruCache.empty(100), commands, conversations),
          new quoteCommand.GreentextListener,
          findCommand.ReactListener,
          voiceKick,
          eventWaiter,
          messageCache)

        val helpCommand = new HelpCommand(commands)
        privateVoiceChats.allCommands.foreach(commands.register)
        voiceKick.allCommands.foreach(commands.register)
        commands register helpCommand
        commands register new PlayCommand(userId = config.owner)
        commands register new StopCommand(this, userId = config.owner)
        commands register new FuriganaCommand
        commands register new BlameCommand
        commands register new BotInfoCommand(userId = config.owner)
        commands register findCommand
        commands register quoteCommand
        commands register new RegisterGuildSlashCommandsCommand(userId = config.owner, slashCommands)
        val readCommand = new ReadCommand(messageCache)
        if (readCommand.available) commands register readCommand
        commands register new PingCommand

        bot.addEventListeners(
          {
            case ev: ReadyEvent =>
              // TODO: Make configurable?
              ev.getJDA.getPresence.setActivity(Activity playing s"Usage: ${commands.prefix}${helpCommand.name}")
              ev.getJDA.setRequiredScopes("bot", "applications.commands")
            case _ =>
          }: EventListener,
          new EventLogger)

        // The discord bot spawns off new threads and its event handlers expect
        // everything to have been set up, so this must come last.
        discord = Some(bot.build())

      case Some(_) =>
        throw new UnsupportedOperationException("Cannot start() the same bot object twice without at least stopping in between.")
    }
  }

  def stop(timeout: Duration = 1 minute): Unit = {
    discord match {
      case Some(bot) =>
        executor.shutdownNow()
        bot.shutdown()
        discord = None
        try executor.awaitTermination(timeout.length, timeout.unit)
        catch {
          case _: InterruptedException =>
        }
        bot.getHttpClient.dispatcher().executorService().shutdown()
        bot.getHttpClient.connectionPool().evictAll()
        try Option(bot.getHttpClient.cache()).foreach(_.close())
        catch {
          case _: IOException =>
        }

      case None =>
        throw new UnsupportedOperationException("Cannot stop() a bot which has not start()ed")
    }
  }
}

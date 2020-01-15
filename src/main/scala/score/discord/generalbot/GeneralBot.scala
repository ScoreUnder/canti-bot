package score.discord.generalbot

import java.io.{File, IOException}
import java.net.URLClassLoader
import java.util.concurrent.{Executors, ScheduledExecutorService}

import com.typesafe.config.ConfigFactory
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.{AccountType, JDA, JDABuilder}
import score.discord.generalbot.collections._
import score.discord.generalbot.command._
import score.discord.generalbot.functionality._
import score.discord.generalbot.functionality.ownership.{DeleteOwnedMessages, MessageOwnership}
import score.discord.generalbot.wrappers.Scheduler
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.language.postfixOps
import CacheCoordinator._
import score.discord.generalbot.functionality.voicekick.VoiceKick

object GeneralBot extends App {
  new GeneralBot().start()
}

class GeneralBot {
  private var discord: Option[JDA] = None
  private var executor: ScheduledExecutorService = _

  def start(): Unit = {
    discord match {
      case None =>
        val bot = new JDABuilder(AccountType.BOT)
        val rawConfig = ConfigFactory.load(URLClassLoader.newInstance(Array(
          new File(".").toURI.toURL
        )))
        val config = Config.load(rawConfig)
        val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("database", rawConfig)
        executor = Executors.newScheduledThreadPool(Runtime.getRuntime.availableProcessors)
        implicit val scheduler = new Scheduler(executor)
        implicit val messageOwnership = new MessageOwnership(new UserByMessage(dbConfig, "message_ownership") withCache LruCache.empty(20000))
        implicit val messageCache = new MessageCache
        implicit val replyCache = new ReplyCache
        val userCreatedChannels = new UserByVoiceChannel(dbConfig, "user_created_channels") withCache LruCache.empty(2000)

        bot.setToken(config.token)

        val commands = new Commands
        val quoteCommand = new QuoteCommand
        val conversations = new Conversations
        val voiceKick = new VoiceKick(userCreatedChannels, new VoiceBanExpiryTable(dbConfig, "voice_ban_expiries"))
        bot.addEventListeners(
          commands,
          new VoiceRoles(new RoleByGuild(dbConfig, "voice_active_role") withCache LruCache.empty(2000), commands),
          new PrivateVoiceChats(userCreatedChannels, commands),
          new DeleteOwnedMessages,
          conversations,
          new Spoilers(new StringByMessage(dbConfig, "spoilers_by_message") withCache LruCache.empty(100), commands, conversations),
          new quoteCommand.GreentextListener,
          voiceKick,
          messageCache)

        val helpCommand = new HelpCommand(commands)
        voiceKick.registerCommands(commands)
        commands register helpCommand
        commands register new PlayCommand(userId = config.owner)
        commands register new StopCommand(this, userId = config.owner)
        commands register new BotInviteCommand
        commands register new FuriganaCommand
        commands register new BlameCommand
        commands register new BotInfoCommand(userId = config.owner)
        commands register new GameStatsCommand
        commands register new FindCommand
        commands register quoteCommand
        val readCommand = new ReadCommand(messageCache)
        if (readCommand.available) commands register readCommand
        commands register new PingCommand

        bot.addEventListeners(
          {
            case ev: ReadyEvent =>
              // TODO: Make configurable?
              ev.getJDA.getPresence.setActivity(Activity playing s"Usage: ${commands.prefix}${helpCommand.name}")
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

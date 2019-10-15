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
import score.discord.generalbot.functionality.ownership.{DatabaseMessageOwnership, DeleteOwnedMessages}
import score.discord.generalbot.wrappers.Scheduler
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.language.postfixOps

object GeneralBot extends App {
  new GeneralBot().start()
}

class GeneralBot {
  private var discord: Option[JDA] = None
  private var executor: ScheduledExecutorService = _

  def start() {
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
        implicit val messageOwnership = new DatabaseMessageOwnership(dbConfig, LruCache.empty(20000))
        implicit val messageCache = new MessageCache
        implicit val replyCache = new ReplyCache

        bot.setToken(config.token)

        val commands = new Commands(new CommandPermissionLookup(dbConfig, LruCache.empty(2000), "command_perms"))
        val quoteCommand = new QuoteCommand
        val conversations = new Conversations
        bot.addEventListeners(
          commands,
          new VoiceRoles(new RoleByGuild(dbConfig, LruCache.empty(2000), "voice_active_role"), commands),
          new PrivateVoiceChats(new UserByVoiceChannel(dbConfig, LruCache.empty(2000), "user_created_channels"), commands),
          new DeleteOwnedMessages,
          conversations,
          new Spoilers(new StringByMessage(dbConfig, LruCache.empty(100), "spoilers_by_message"), commands, conversations),
          new quoteCommand.GreentextListener,
          messageCache)

        val helpCommand = new HelpCommand(commands)
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

  def stop(timeout: Duration = 1 minute) {
    discord match {
      case Some(bot) =>
        executor.shutdown()
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

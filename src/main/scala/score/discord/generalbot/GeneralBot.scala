package score.discord.generalbot

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.{Executors, ScheduledExecutorService}

import com.typesafe.config.ConfigFactory
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.events.guild.voice.{GuildVoiceJoinEvent, GuildVoiceLeaveEvent, GuildVoiceMoveEvent}
import net.dv8tion.jda.core.events.message.guild.GenericGuildMessageEvent
import net.dv8tion.jda.core.events.message.{MessageDeleteEvent, MessageReceivedEvent, MessageUpdateEvent}
import net.dv8tion.jda.core.events.user.GenericUserEvent
import net.dv8tion.jda.core.events.{DisconnectEvent, Event, ReadyEvent, StatusChangeEvent}
import net.dv8tion.jda.core.hooks.EventListener
import net.dv8tion.jda.core.{AccountType, JDA, JDABuilder}
import org.apache.commons.lang3.time.FastDateFormat
import score.discord.generalbot.collections._
import score.discord.generalbot.command._
import score.discord.generalbot.functionality._
import score.discord.generalbot.functionality.ownership.{DatabaseMessageOwnership, DeleteOwnedMessages}
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.language.postfixOps

object GeneralBot extends App {
  new GeneralBot().start()
}

class GeneralBot {
  private var discord: Either[JDABuilder, JDA] = Left(new JDABuilder(AccountType.BOT))
  private var executor: ScheduledExecutorService = _

  def start() {
    discord match {
      case Left(bot) =>
        val rawConfig = ConfigFactory.load(URLClassLoader.newInstance(Array(
          new File(".").toURI.toURL
        )))
        val config = Config.load(rawConfig)
        val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("database", rawConfig)
        executor = Executors.newScheduledThreadPool(Runtime.getRuntime.availableProcessors)
        implicit val scheduler = new Scheduler(executor)
        implicit val messageOwnership = new DatabaseMessageOwnership(dbConfig, LruCache.empty(20000))

        bot.setToken(config.token)

        val commands = new Commands(new CommandPermissionLookup(dbConfig, LruCache.empty(2000), "command_perms"))
        bot addEventListener commands
        bot addEventListener new VoiceRoles(new RoleByGuild(dbConfig, LruCache.empty(2000), "voice_active_role"), commands)
        bot addEventListener new TableFlip
        bot addEventListener new PrivateVoiceChats(new UserByChannel(dbConfig, LruCache.empty(2000), "user_created_channels"), commands)
        bot addEventListener new DeleteOwnedMessages
        bot addEventListener new Spoilers(new StringByMessage(dbConfig, LruCache.empty(100), "spoilers_by_message"), commands)

        val helpCommand = new HelpCommand(commands)
        commands register helpCommand
        commands register new PlayCommand(userId = config.owner)
        commands register new StopCommand(this, userId = config.owner)
        commands register new RestrictCommand(commands)
        commands register new BotInviteCommand
        commands register new FuriganaCommand(commands)
        commands register new BlameCommand(commands)
        val readCommand = new ReadCommand(commands)
        if (readCommand.available) commands register readCommand

        bot addEventListener new EventListener {
          private[this] val format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ssZ")

          def log(msg: String): Unit = println(s"${format format System.currentTimeMillis} $msg")

          override def onEvent(event: Event) = event match {
            case ev: ReadyEvent =>
              // TODO: Make configurable?
              ev.getJDA.getPresence.setGame(Game of s"Usage: ${commands.prefix}${helpCommand.name}")
              log("Bot is ready.")
            case ev: StatusChangeEvent =>
              log(s"Bot status changed to ${ev.getStatus}")
            case ev: DisconnectEvent =>
              ev.getCloseCode match {
                case null => log("Disconnected, no reason provided.")
                case code => log(s"Disconnected. code=${code.getCode} meaning=${code.getMeaning}")
              }
            case ev: MessageReceivedEvent =>
              log(s"MESSAGE: ${ev.getMessage.rawId} ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
                ev.getMessage.getRawContent.split('\n').map("\t" + _).mkString("\n"))
            case ev: MessageDeleteEvent =>
              log(s"DELETED: ${ev.getChannel.unambiguousString} id=${ev.getMessageIdLong}")
            case ev: MessageUpdateEvent =>
              log(s"EDITED: ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
                ev.getMessage.getRawContent.split('\n').map("\t" + _).mkString("\n"))
            case ev: GuildVoiceJoinEvent =>
              log(s"VOICE JOIN: ${ev.getMember.getUser.unambiguousString} in ${ev.getChannelJoined.unambiguousString}")
            case ev: GuildVoiceLeaveEvent =>
              log(s"VOICE PART: ${ev.getMember.getUser.unambiguousString} from ${ev.getChannelLeft.unambiguousString}")
            case ev: GuildVoiceMoveEvent =>
              log(s"VOICE MOVE: ${ev.getMember.getUser.unambiguousString} from " +
                s"${ev.getChannelLeft.unambiguousString} to ${ev.getChannelJoined.unambiguousString}")
            case _: GenericUserEvent | _: GenericGuildMessageEvent =>
            // Ignored (they're pretty boring)
            case ev =>
              log(ev.getClass.toGenericString)
          }
        }

        // The discord bot spawns off new threads and its event handlers expect
        // everything to have been set up, so this must come last.
        discord = Right(bot.buildBlocking())

      case Right(_) =>
        throw new UnsupportedOperationException("Cannot start() the same bot object twice without at least stopping in between.")
    }
  }

  def stop(timeout: Duration = 1 minute) {
    discord match {
      case Right(bot) =>
        executor.shutdown()
        bot.shutdown()
        discord = Left(new JDABuilder(AccountType.BOT))
        try executor.awaitTermination(timeout.length, timeout.unit)
        catch {
          case _: InterruptedException =>
        }

      case Left(_) =>
        throw new UnsupportedOperationException("Cannot stop() a bot which has not start()ed")
    }
  }
}

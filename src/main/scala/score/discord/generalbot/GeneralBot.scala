package score.discord.generalbot

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.{Executors, ScheduledExecutorService}

import com.typesafe.config.ConfigFactory
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.events.message.{MessageDeleteEvent, MessageReceivedEvent, MessageUpdateEvent}
import net.dv8tion.jda.core.events.user.GenericUserEvent
import net.dv8tion.jda.core.events.{DisconnectEvent, ReadyEvent, StatusChangeEvent}
import net.dv8tion.jda.core.hooks.EventListener
import net.dv8tion.jda.core.{AccountType, JDA, JDABuilder}
import score.discord.generalbot.collections.{CommandPermissionLookup, RoleByGuild, UserByChannel}
import score.discord.generalbot.command._
import score.discord.generalbot.functionality.ownership.{DeleteOwnedMessages, MemoryMessageOwnership}
import score.discord.generalbot.functionality.{Commands, PrivateVoiceChats, TableFlip, VoiceRoles}
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
        implicit val messageOwnership = new MemoryMessageOwnership(20000)

        bot.setToken(config.token)

        val commands = new Commands(new CommandPermissionLookup(dbConfig, "command_perms"))
        bot addEventListener commands
        bot addEventListener new VoiceRoles(new RoleByGuild(dbConfig, "voice_active_role"), commands)
        bot addEventListener new TableFlip
        bot addEventListener new PrivateVoiceChats(new UserByChannel(dbConfig, "user_created_channels"), commands)
        bot addEventListener new DeleteOwnedMessages

        val helpCommand = new HelpCommand(commands)
        commands register helpCommand
        commands register new PlayCommand(userId = config.owner)
        commands register new StopCommand(this, userId = config.owner)
        commands register new RestrictCommand(commands)
        commands register new BotInviteCommand
        commands register new FuriganaCommand(commands)

        bot addEventListener ({
          case ev: ReadyEvent =>
            // TODO: Make configurable?
            ev.getJDA.getPresence.setGame(Game of s"Usage: ${commands.prefix}${helpCommand.name}")
            println("Bot is ready.")
          case ev: StatusChangeEvent =>
            println(s"Bot status changed to ${ev.getStatus}")
          case ev: DisconnectEvent =>
            ev.getCloseCode match {
              case null => println("Disconnected, no reason provided.")
              case code => println(s"Disconnected. code=${code.getCode} meaning=${code.getMeaning}")
            }
          case ev: MessageReceivedEvent =>
            println(s"MESSAGE: ${ev.getMessage.id} ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
              ev.getMessage.getRawContent.split('\n').map("\t" + _).mkString("\n"))
          case ev: MessageDeleteEvent =>
            println(s"DELETED: ${ev.getChannel.unambiguousString} id=${ev.getMessageIdLong}")
          case ev: MessageUpdateEvent =>
            println(s"EDITED: ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
              ev.getMessage.getRawContent.split('\n').map("\t" + _).mkString("\n"))
          case _: GenericUserEvent =>
            // Ignored (they're pretty boring)
          case ev =>
            println(ev.getClass)
        }: EventListener)

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
        bot.shutdown(true)
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

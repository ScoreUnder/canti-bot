package score.discord.generalbot

import java.io.File
import java.util.concurrent.{Executors, ScheduledExecutorService}

import net.dv8tion.jda.core.events.message.{MessageDeleteEvent, MessageReceivedEvent, MessageUpdateEvent}
import net.dv8tion.jda.core.events.{DisconnectEvent, ReadyEvent}
import net.dv8tion.jda.core.hooks.EventListener
import net.dv8tion.jda.core.{AccountType, JDA, JDABuilder, events}
import score.discord.generalbot.command.{HelpCommand, PlayCommand, StopCommand}
import score.discord.generalbot.functionality.{Commands, PrivateVoiceChats, TableFlip, VoiceRoles}
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._
import slick.jdbc.SQLiteProfile.api._

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
        val config = Config.load(new File("config.yml"))
        val database = Database.forURL("jdbc:sqlite:state.db", driver = "org.sqlite.JDBC")
        executor = Executors.newScheduledThreadPool(Runtime.getRuntime.availableProcessors)
        implicit val scheduler = new Scheduler(executor)

        bot.setToken(config.token)

        val commands = new Commands()
        bot addEventListener commands
        bot addEventListener new VoiceRoles(database, commands)
        bot addEventListener new TableFlip
        bot addEventListener new PrivateVoiceChats(database, commands)

        commands register new HelpCommand(commands)
        commands register new PlayCommand(userId = config.owner)
        commands register new StopCommand(this, userId = config.owner)

        bot addEventListener new EventListener {
          override def onEvent(_event: events.Event) {
            println(_event match {
              case _: ReadyEvent =>
                "Bot is ready."
              case ev: DisconnectEvent =>
                s"Disconnected. code=${ev.getCloseCode.getCode} meaning=${ev.getCloseCode.getMeaning}"
              case ev: MessageReceivedEvent =>
                s"MESSAGE: ${ev.getMessage.id} ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
                  ev.getMessage.getRawContent.split('\n').map("\t" + _).mkString("\n")
              case ev: MessageDeleteEvent =>
                s"DELETED: ${ev.getChannel.unambiguousString} id=${ev.getMessageIdLong}"
              case ev: MessageUpdateEvent =>
                s"EDITED: ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
                  ev.getMessage.getRawContent.split('\n').map("\t" + _).mkString("\n")
              case _ =>
                s"${_event.getClass}"
            })
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

package score.discord.generalbot

import java.awt.Color
import java.io.File

import net.dv8tion.jda.core.hooks.EventListener
import net.dv8tion.jda.core.{AccountType, JDA, JDABuilder, events}
import score.discord.generalbot.command.HelpCommand
import score.discord.generalbot.functionality.{Commands, VoiceRoles}
import slick.jdbc.SQLiteProfile.api._

import scala.collection.JavaConverters._

class GeneralBot {
  val config = Config.load(new File("config.yml"))
  val database = Database.forURL("jdbc:sqlite:state.db", driver = "org.sqlite.JDBC")

  val bot = new JDABuilder(AccountType.BOT)
  bot.setToken(config.token)

  val commands = new Commands()
  bot.addEventListener(commands)
  bot.addEventListener(new VoiceRoles(database, commands))

  commands.registerCommand(new HelpCommand(commands))

  bot.addEventListener(new EventListener {
    override def onEvent(_event: events.Event) {
      println(_event match {
        case ev: events.ReadyEvent =>
          "Ready."
        case ev: events.StatusChangeEvent =>
          if (ev.getStatus == JDA.Status.CONNECTED) {
            ev.getJDA.getGuilds.asScala.foreach((guild) =>
              guild.getVoiceStates.asScala.foreach((state) =>
                println(s"${Option(state.getChannel).map(_.getName).getOrElse("None")} ${state.getMember.getUser.getName} deaf?${Option(state).map(_.isDeafened).getOrElse("XX")}")
              )
            )
          }
          s"STATUS: ${ev.getOldStatus} => ${ev.getStatus}"
        case ev: events.DisconnectEvent =>
          s"Disconnected. ${ev.getCloseCode.getCode} ${ev.getCloseCode.getMeaning}"
        case ev: events.message.MessageReceivedEvent =>
          s"MESSAGE: ${ev.getChannel.getName} ${ev.getAuthor.getName}\n" +
            ev.getMessage.getRawContent.split('\n').map("\t" + _).mkString("\n")
        case ev: events.guild.voice.GuildVoiceDeafenEvent =>
          s"DEAFEN: ${ev.isDeafened} ${ev.getMember.getVoiceState.getChannel.getName} ${ev.getMember.getUser.getName}"
        case ev: events.guild.voice.GuildVoiceJoinEvent =>
          s"VOICE JOIN: ${ev.getChannelJoined.getName} ${ev.getMember.getUser.getName}"
        case ev: events.guild.voice.GuildVoiceLeaveEvent =>
          s"VOICE LEAVE: ${ev.getChannelLeft.getName} ${ev.getMember.getUser.getName}"
        case ev: events.guild.voice.GuildVoiceGuildDeafenEvent =>
          s"VOICE GUILD DEAFEN: ${ev.isGuildDeafened} ${ev.getMember.getUser.getName}"
        case _ =>
          s"${_event.getClass}"
      })
    }
  })
  bot.buildBlocking()
}

object GeneralBot {
  val ERROR_COLOR = new Color(240, 100, 100)
  val OKAY_COLOR = new Color(100, 130, 240)
}

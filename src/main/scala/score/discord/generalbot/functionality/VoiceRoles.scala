package score.discord.generalbot.functionality

import net.dv8tion.jda.core.entities.{GuildVoiceState, Member, Message}
import net.dv8tion.jda.core.events.guild.voice.{GenericGuildVoiceEvent, GuildVoiceDeafenEvent, GuildVoiceJoinEvent, GuildVoiceLeaveEvent}
import net.dv8tion.jda.core.events.{Event, ReadyEvent}
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.command.Command
import score.discord.generalbot.util.{BotMessages, RoleByGuild}
import score.discord.generalbot.wrappers.Conversions._
import slick.jdbc.SQLiteProfile.api._

import scala.collection.JavaConverters._
import scala.util.Try

class VoiceRoles(database: Database, commands: Commands) extends EventListener {
  private val roleByGuild = new RoleByGuild(database, "voice_active_role")

  commands register new Command.ServerAdminOnly {
    override def name = "setvoicerole"

    override def aliases = Nil

    override def description = "Sets the role automatically assigned to voice chat users"

    override def execute(message: Message, args: String) {
      val roleName = args.trim
      val matchingRoles = Try(roleName.toLong).
        map(id => List(message.getGuild.getRoleById(id))).
        getOrElse(message.getGuild.getRolesByName(roleName, true).asScala)

      matchingRoles match {
        case Seq() =>
          message.getChannel ! BotMessages.error("Could not find a role by that name.").
            addField("Requested by", s"<@!${message.getAuthor.getIdLong}>", true).
            addField("Search term", roleName, true)

        case Seq(role) =>
          roleByGuild(message.getGuild) = role
          message.getChannel ! BotMessages.okay(s"Set the new voice chat role to <@&${role.getIdLong}>").
            addField("Requested by", s"<@!${message.getAuthor.getIdLong}>", true)

        case Seq(_*) =>
          val embed = BotMessages.error("Too many roles by that name.").
            addField("Requested by", s"<@!${message.getAuthor.getIdLong}>", true).
            addField("Search term", roleName, true)

          for (role <- matchingRoles) {
            embed.appendDescription(s"\n${role.getIdLong}: <@&${role.getIdLong}>")
          }

          message.getChannel ! embed
      }
    }
  }

  commands register new Command.Anyone {
    override def name = "voicerole"

    override def aliases = List("getvoicerole")

    override def description = "Check the voice chat role"

    override def execute(message: Message, args: String) = {
      roleByGuild(message.getGuild) match {
        case Some(role) =>
          message.getChannel ! BotMessages.okay(s"The voice chat role is currently set to <@&${role.getIdLong}>.")

        case None =>
          message.getChannel ! BotMessages.plain("There is currently no voice chat role set.")
      }
    }
  }

  commands register new Command.ServerAdminOnly {
    override def name = "delvoicerole"

    override def aliases = List("rmvoicerole", "removevoicerole", "clearvoicerole")

    override def description = "Clears the voice chat role (i.e. stops tagging voice chat users)"

    override def execute(message: Message, args: String) = {
      roleByGuild remove message.getGuild
    }
  }

  private def attachRole(member: Member, force: Boolean): Unit = {
    for (role <- roleByGuild(member.getGuild)
         if force || !(member has role)) {
      member.roles += role
    }
  }

  private def removeRole(member: Member, force: Boolean): Unit = {
    for (role <- roleByGuild(member.getGuild)
         if force || (member has role)) {
      member.roles -= role
    }
  }

  private def correctRole(state: GuildVoiceState, force: Boolean = false): Unit = {
    if (state.inVoiceChannel && !state.isDeafened) {
      attachRole(state.getMember, force)
    } else {
      removeRole(state.getMember, force)
    }
  }

  override def onEvent(event: Event) = {
    event match {
      case ev: ReadyEvent =>
        for (guild <- ev.getJDA.getGuilds.asScala;
             voiceState <- guild.getVoiceStates.asScala) {
          correctRole(voiceState)
        }

      case ev: GenericGuildVoiceEvent =>
        correctRole(ev.getVoiceState, force = ev match {
          case _: GuildVoiceDeafenEvent | _: GuildVoiceJoinEvent | _: GuildVoiceLeaveEvent =>
            true
          case _ => false
        })

      case _ =>
    }
  }
}

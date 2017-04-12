package score.discord.generalbot.functionality

import java.awt.Color

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.{GuildVoiceState, Member, Message, VoiceState}
import net.dv8tion.jda.core.events.{Event, ReadyEvent}
import net.dv8tion.jda.core.events.guild.voice.{GenericGuildVoiceEvent, GuildVoiceDeafenEvent, GuildVoiceJoinEvent, GuildVoiceLeaveEvent}
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.GeneralBot
import score.discord.generalbot.command.Command
import score.discord.generalbot.util.RoleByGuild
import slick.jdbc.SQLiteProfile.api._

import scala.collection.JavaConverters._
import scala.util.Try

class VoiceRoles(database: Database, commands: Commands) extends EventListener {
  private val roleByGuild = new RoleByGuild(database, "voice_active_role")

  commands.registerCommand(new Command {
    override def name = "setvoicerole"

    override def aliases = Nil

    override def description = "Sets the role automatically assigned to voice chat users"

    override def isAdminOnly = true

    override def execute(message: Message, args: String) {
      val roleName = args.trim
      val matchingRoles = Try(roleName.toLong).
        map(id => List(message.getGuild.getRoleById(id))).
        getOrElse(message.getGuild.getRolesByName(roleName, true).asScala)

      matchingRoles match {
        case Seq() =>
          message.getChannel.sendMessage(
            new EmbedBuilder().
              appendDescription("Could not find a role by that name.").
              setColor(GeneralBot.ERROR_COLOR).
              addField("Requested by", s"<@!${message.getAuthor.getIdLong}>", true).
              addField("Search term", roleName, true).
              build()
          ).queue()
        case Seq(role) =>
          roleByGuild(message.getGuild) = role
          message.getChannel.sendMessage(
            new EmbedBuilder().
              appendDescription(s"Set the new voice chat role to <@&${role.getIdLong}>").
              setColor(GeneralBot.OKAY_COLOR).
              addField("Requested by", s"<@!${message.getAuthor.getIdLong}>", true).
              build()
          ).queue()
        case Seq(_*) =>
          val embed =
            new EmbedBuilder().
              appendDescription("Too many roles by that name.").
              setColor(GeneralBot.ERROR_COLOR).
              addField("Requested by", s"<@!${message.getAuthor.getIdLong}>", true).
              addField("Search term", roleName, true)

          for (role <- matchingRoles) {
            embed.appendDescription(s"\n${role.getIdLong}: <@&${role.getIdLong}>")
          }

          message.getChannel.sendMessage(embed.build()).queue()
      }
    }
  })

  commands.registerCommand(new Command {
    override def name = "voicerole"

    override def aliases = List("getvoicerole")

    override def description = "Check the current voice chat role"

    override def isAdminOnly = false

    override def execute(message: Message, args: String) = {
      roleByGuild(message.getGuild) match {
        case Some(role) =>
          val embed =
            new EmbedBuilder().
              appendDescription(s"The voice chat role is currently set to <@&${role.getIdLong}>.")
          message.getChannel.sendMessage(embed.build()).queue()

        case None =>
          val embed =
            new EmbedBuilder().
              appendDescription("There is currently no voice chat role set.")
          message.getChannel.sendMessage(embed.build()).queue()
      }
    }
  })

  private def attachRole(member: Member, force: Boolean): Unit = {
    for (role <- roleByGuild(member.getGuild)) {
      if (force || !member.getRoles.contains(role)) {
        member.getGuild.getController.addRolesToMember(member, role).queue()
      }
    }
  }

  private def removeRole(member: Member, force: Boolean): Unit = {
    for (role <- roleByGuild(member.getGuild)) {
      if (force || member.getRoles.contains(role)) {
        member.getGuild.getController.removeRolesFromMember(member, role).queue()
      }
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

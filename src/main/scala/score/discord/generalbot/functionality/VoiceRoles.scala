package score.discord.generalbot.functionality

import java.util.concurrent.ConcurrentHashMap

import net.dv8tion.jda.core.entities.{GuildVoiceState, Member, Message}
import net.dv8tion.jda.core.events.guild.voice.GenericGuildVoiceEvent
import net.dv8tion.jda.core.events.{Event, ReadyEvent}
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.command.Command
import score.discord.generalbot.util.{BotMessages, CommandHelper, GuildUserId, RoleByGuild}
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._
import slick.jdbc.SQLiteProfile.api._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

class VoiceRoles(database: Database, commands: Commands)(implicit scheduler: Scheduler) extends EventListener {
  private val roleByGuild = new RoleByGuild(database, "voice_active_role")

  commands register new Command.ServerAdminOnly {
    override def name = "setvoicerole"

    override def aliases = Nil

    override def description = "Set the role automatically assigned to voice chat users"

    override def execute(message: Message, args: String) {
      val roleName = args.trim
      val matchingRoles = Try(roleName.toLong).
        map(id => List(message.getGuild.getRoleById(id))).
        getOrElse(message.getGuild.getRolesByName(roleName, true).asScala)

      matchingRoles match {
        case Nil =>
          message.getChannel ! BotMessages.error("Could not find a role by that name.").
            addField("Requested by", s"<@!${message.getAuthor.id}>", true).
            addField("Search term", roleName, true)

        case Seq(role) =>
          roleByGuild(message.getGuild) = role
          message.getChannel ! BotMessages.okay(s"Set the new voice chat role to <@&${role.id}>").
            addField("Requested by", s"<@!${message.getAuthor.id}>", true)

        case Seq(_*) =>
          val embed = BotMessages.error("Too many roles by that name.").
            addField("Requested by", s"<@!${message.getAuthor.id}>", true).
            addField("Search term", roleName, true)

          for (role <- matchingRoles) {
            embed.appendDescription(s"\n${role.id}: <@&${role.id}>")
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
      message.getChannel ! (
        for (guild <- CommandHelper(message).guild.left.map(BotMessages.error);
             role <- roleByGuild(guild).toRight(BotMessages.plain("There is currently no voice chat role set.")))
          yield BotMessages okay s"The voice chat role is currently set to <@&${role.id}>."
        ).fold(identity, identity).toMessage
    }
  }

  commands register new Command.ServerAdminOnly {
    override def name = "delvoicerole"

    override def aliases = List("rmvoicerole", "removevoicerole", "clearvoicerole")

    override def description = "Clear the voice chat role (i.e. stops tagging voice chat users)"

    override def execute(message: Message, args: String) = {
      roleByGuild remove message.getGuild
    }
  }

  private def setRole(member: Member, shouldHaveRole: Boolean, force: Boolean): Boolean = {
    var changed = false
    for (role <- roleByGuild(member.getGuild)
         if force || shouldHaveRole != (member has role)) {
      if (shouldHaveRole)
        member.roles += role
      else
        member.roles -= role
      changed = true
    }
    changed
  }

  private def correctRole(member: Member, force: Boolean = false): Boolean = {
    setRole(member, shouldHaveRole(member.getVoiceState), force)
  }

  private def shouldHaveRole(state: GuildVoiceState) = state.inVoiceChannel && !state.isDeafened

  val pendingRoleUpdates = new ConcurrentHashMap[GuildUserId, GuildUserId]

  private def queueRoleUpdate(member: Member): Unit = {
    /*
      Why queue role updates?
      Because the "join"/"deafen" events come one after the other,
      and that often means that we see a join, add a role, see a deafen,
      and remove the role. Then we get the role updates from the server
      after all that, which means if we were only changing roles that
      looked like they needed changing, we would miss the change on the
      deafen event (because it's after a server-side role change but before
      it sends the update to our client).
     */
    val memberId = GuildUserId(member)

    def updateRole() = {
      pendingRoleUpdates remove memberId
      // TODO: No thread-safe way to do this
      correctRole(member)
    }

    def queueUpdate() =
      pendingRoleUpdates.put(memberId, memberId) match {
        case null => scheduler.schedule(50 milliseconds) { updateRole() }
        case _ =>
      }

    // This holds locks, so do it away from the main thread
    scheduler.submit { queueUpdate() }
  }

  override def onEvent(event: Event) = {
    event match {
      case ev: ReadyEvent =>
        val jda = ev.getJDA
        scheduler.schedule(0 minutes, 1 minute) {
          for (guild <- jda.guilds;
               voiceState <- guild.voiceStates) {
            queueRoleUpdate(voiceState.getMember)
          }
        }

      case ev: GenericGuildVoiceEvent =>
        queueRoleUpdate(ev.getMember)

      case _ =>
    }
  }
}


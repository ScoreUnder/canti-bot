package score.discord.generalbot.functionality

import java.util.concurrent.{ConcurrentHashMap, ScheduledFuture, ThreadLocalRandom}

import net.dv8tion.jda.core.entities.{GuildVoiceState, Member, Message}
import net.dv8tion.jda.core.events.guild.voice.GenericGuildVoiceEvent
import net.dv8tion.jda.core.events.{Event, ReadyEvent}
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.collections.RoleByGuild
import score.discord.generalbot.command.Command
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.ParseUtils._
import score.discord.generalbot.util.{BotMessages, CommandHelper, GuildUserId}
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.concurrent.duration._
import scala.language.postfixOps

class VoiceRoles(roleByGuild: RoleByGuild, commands: Commands)(implicit scheduler: Scheduler, messageOwnership: MessageOwnership) extends EventListener {
  commands register new Command.ServerAdminOnly {
    override def name = "setvoicerole"

    override def aliases = Nil

    override def description = "Set the role automatically assigned to voice chat users"

    override def execute(message: Message, args: String) {
      message.getChannel.sendOwned(findRole(message.getGuild, args.trim).fold(
        identity,
        { role =>
          roleByGuild(message.getGuild) = role
          BotMessages.okay(s"Set the new voice chat role to ${role.mention}")
        }).addField("Requested by", message.getAuthor.mention, true),
        owner = message.getAuthor
      )
    }
  }

  commands register new Command.Anyone {
    override def name = "voicerole"

    override def aliases = List("getvoicerole")

    override def description = "Check the voice chat role"

    override def execute(message: Message, args: String) = {
      message.getChannel.sendOwned((
        for (guild <- CommandHelper(message).guild.left.map(BotMessages.error);
             role <- roleByGuild(guild).toRight(BotMessages.plain("There is currently no voice chat role set.")))
          yield BotMessages okay s"The voice chat role is currently set to ${role.mention}."
        ).fold(identity, identity).toMessage,
        owner = message.getAuthor
      )
    }
  }

  commands register new Command.ServerAdminOnly {
    override def name = "delvoicerole"

    override def aliases = List("rmvoicerole", "removevoicerole", "clearvoicerole")

    override def description = "Clear the voice chat role (i.e. stops tagging voice chat users)"

    override def execute(message: Message, args: String) = {
      roleByGuild remove message.getGuild
      message.addReaction("👌").queue()
    }
  }

  private def setRole(member: Member, shouldHaveRole: Boolean): Boolean = {
    var changed = false
    for (role <- roleByGuild(member.getGuild)
         if shouldHaveRole != (member has role)) {
      if (shouldHaveRole)
        member.roles += role
      else
        member.roles -= role
      changed = true
    }
    changed
  }

  private def correctRole(member: Member): Boolean = {
    setRole(member, shouldHaveRole(member.getVoiceState))
  }

  private def shouldHaveRole(state: GuildVoiceState) =
    !state.getMember.getUser.isBot && !state.isDeafened && Option(state.getChannel).exists(_ != state.getGuild.getAfkChannel)

  private val pendingRoleUpdates = new ConcurrentHashMap[GuildUserId, ScheduledFuture[_]]
  private[this] val rng = ThreadLocalRandom.current()

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

    def queueUpdate() = {
      // Delay to ensure that rapid switching of deafen doesn't run our
      // rate limits out.
      val newFuture = scheduler.schedule((200 + rng.nextInt(300)) milliseconds) {
        updateRole()
      }
      val previousFuture = pendingRoleUpdates.put(memberId, newFuture)
      previousFuture match {
        case null =>
        case future => future.cancel(false)
      }
    }

    queueUpdate()
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


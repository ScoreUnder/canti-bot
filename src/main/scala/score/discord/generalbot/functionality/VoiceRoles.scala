package score.discord.generalbot.functionality

import java.util.concurrent.{ConcurrentHashMap, ScheduledFuture, ThreadLocalRandom}

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Guild, GuildVoiceState, Member, Message, Role}
import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.generalbot.collections.{AsyncMap, ReplyCache}
import score.discord.generalbot.command.Command
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.ParseUtils._
import score.discord.generalbot.util.{APIHelper, BotMessages, CommandHelper, GuildUserId}
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.IdConversions._
import score.discord.generalbot.wrappers.jda.ID

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class VoiceRoles(roleByGuild: AsyncMap[ID[Guild], ID[Role]], commands: Commands)(implicit scheduler: Scheduler, messageOwnership: MessageOwnership, replyCache: ReplyCache) extends EventListener {
  commands register new Command.ServerAdminOnly {
    override def name = "setvoicerole"

    override def aliases = Nil

    override def description = "Set the role automatically assigned to voice chat users"

    override def execute(message: Message, args: String): Unit = {
      message.reply(
        if (args.isEmpty) BotMessages.error("Please provide a role name to use as the voice role")
        else
          findRole(message.getGuild, args.trim).fold(
            identity, { role =>
              roleByGuild(message.getGuild.id) = role.id  // TODO: Handle exceptions from Future
              BotMessages.okay(s"Set the new voice chat role to ${role.mention}")
            }).addField("Requested by", message.getAuthor.mentionWithName, true)
      )
    }
  }

  commands register new Command.Anyone {
    override def name = "voicerole"

    override def aliases = List("getvoicerole")

    override def description = "Check the voice chat role"

    override def execute(message: Message, args: String): Unit = {
      async {
        implicit val jda = message.getJDA
        message.reply(
          (CommandHelper(message).guild match {
            case Left(err) => BotMessages error err
            case Right(guild) =>
              await(roleByGuild.get(guild.id))
                .flatMap(_.find)
                .toRight(BotMessages.plain("There is currently no voice chat role set."))
                .map(role => BotMessages okay s"The voice chat role is currently set to ${role.mention}.")
                .fold(identity, identity)
          }).toMessage
        )
      }
    }
  }

  commands register new Command.ServerAdminOnly {
    override def name = "delvoicerole"

    override def aliases = List("rmvoicerole", "removevoicerole", "clearvoicerole")

    override def description = "Clear the voice chat role (i.e. stops tagging voice chat users)"

    override def execute(message: Message, args: String): Unit = {
      async {
        await(roleByGuild remove message.getGuild.id)
        message.addReaction("👌").queue()
      }.failed.foreach(APIHelper.loudFailure("removing voice role", message.getChannel))
    }
  }

  private def setRole(member: Member, role: Role, shouldHaveRole: Boolean): Unit = {
    if (shouldHaveRole != (member has role)) {
      if (shouldHaveRole)
        member.roles += role -> "voice state change"
      else
        member.roles -= role -> "voice state change"
    }
  }

  private def shouldHaveRole(state: GuildVoiceState) =
    !state.getMember.getUser.isBot && !state.isDeafened && Option(state.getChannel).exists(_ != state.getGuild.getAfkChannel)

  private val pendingRoleUpdates = new ConcurrentHashMap[GuildUserId, ScheduledFuture[Unit]]
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
    async {
      val memberId = GuildUserId(member)

      def updateRole(role: Role) {
        pendingRoleUpdates remove memberId
        // TODO: No thread-safe way to do this
        setRole(member, role, shouldHaveRole(member.getVoiceState))
      }

      def queueUpdate(role: Role) {
        // Delay to ensure that rapid switching of deafen doesn't run our
        // rate limits out.
        val newFuture = scheduler.schedule((200 + rng.nextInt(300)) milliseconds) {
          updateRole(role)
        }
        val previousFuture = pendingRoleUpdates.put(memberId, newFuture)
        previousFuture match {
          case null =>
          case future => future.cancel(false)
        }
      }

      implicit val jda: JDA = member.getJDA
      await(roleByGuild.get(member.getGuild.id)).flatMap(_.find).foreach(queueUpdate)
    }
  }

  override def onEvent(event: GenericEvent): Unit = {
    event match {
      case ev: ReadyEvent =>
        val jda = ev.getJDA
        scheduler.schedule(initialDelay = 0 minutes, delay = 1 minute) {
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


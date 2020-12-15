package score.discord.generalbot.functionality

import java.util.concurrent.{ConcurrentHashMap, ScheduledFuture, ThreadLocalRandom}

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities._
import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.generalbot.collections.{AsyncMap, ReplyCache}
import score.discord.generalbot.command.{Command, ReplyingCommand}
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.ParseUtils._
import score.discord.generalbot.util.{APIHelper, BotMessages, GuildUserId}
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.IdConversions._

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.chaining.scalaUtilChainingOps

class VoiceRoles(roleByGuild: AsyncMap[ID[Guild], ID[Role]], commands: Commands)(implicit scheduler: Scheduler, messageOwnership: MessageOwnership, replyCache: ReplyCache) extends EventListener {
  commands register new ReplyingCommand with Command.ServerAdminOnly {
    override def name = "voicerole"

    override def aliases: List[String] = List("setvoicerole", "getvoicerole")

    override def description = "Set or remove the role automatically assigned to voice chat users"

    override def longDescription(invocation: String): String =
      s"""Usage: `$invocation In Voice` or `$invocation 123456789` (with a role ID)
         |You can also remove the role with `$invocation none`""".stripMargin

    override def executeAndGetMessage(message: Message, args: String): Future[Message] = async {
      await(args.trim match {
        case "" => showVoiceRole(message.getGuild)
        case "none" => delVoiceRole(message.getGuild)
        case _ => setVoiceRole(args.trim, message.getGuild)
      }).toMessage
    }.tap(_.failed.foreach(APIHelper.loudFailure("setting voice role", message)))

    private def setVoiceRole(roleName: String, guild: Guild) = async {
      findRole(guild, roleName) match {
        case Left(err) => err
        case Right(role) =>
          await(roleByGuild(guild.id) = role.id)
          BotMessages.okay(s"Set the new voice chat role to ${role.mention}")
      }
    }

    private def delVoiceRole(guild: Guild) = async {
      await(roleByGuild remove guild.id)
      BotMessages.okay(s"Turned off voice chat roles for this server")
    }

    private def showVoiceRole(guild: Guild) = async {
      implicit val jda: JDA = guild.getJDA
      await(roleByGuild.get(guild.id))
        .flatMap(_.find)
        .fold(
          BotMessages.plain("There is currently no voice chat role set."))(
          role => BotMessages.okay(s"The voice chat role is currently set to ${role.mention}."))
    }

    override implicit def messageOwnership: MessageOwnership = VoiceRoles.this.messageOwnership

    override implicit def replyCache: ReplyCache = VoiceRoles.this.replyCache
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

      def updateRole(role: Role): Unit = {
        pendingRoleUpdates remove memberId
        // TODO: No thread-safe way to do this
        setRole(member, role, shouldHaveRole(member.getVoiceState))
      }

      def queueUpdate(role: Role): Unit = {
        // Delay to ensure that rapid switching of deafen doesn't run our
        // rate limits out.
        val newFuture = scheduler.schedule((200 + rng.nextInt(300)) milliseconds) {
          updateRole(role)
        }
        val previousFuture = Option(pendingRoleUpdates.put(memberId, newFuture))
        previousFuture.foreach(_.cancel(false))
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


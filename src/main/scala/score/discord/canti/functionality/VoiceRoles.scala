package score.discord.canti.functionality

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.canti.collections.{AsyncMap, ReplyCache}
import score.discord.canti.command.{Command, ReplyingCommand}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.ParseUtils.*
import score.discord.canti.util.{APIHelper, BotMessages, GuildUserId}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.jda.Conversions.{richMember, richRole}
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.IdConversions.*
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RichGuild.voiceStates
import score.discord.canti.wrappers.jda.RichJDA.guilds
import score.discord.canti.wrappers.jda.RichSnowflake.id

import java.util.concurrent.{ConcurrentHashMap, ScheduledFuture, ThreadLocalRandom}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.language.{implicitConversions, postfixOps}
import scala.util.chaining.scalaUtilChainingOps

class VoiceRoles(roleByGuild: AsyncMap[ID[Guild], ID[Role]])(using
  scheduler: Scheduler,
  messageOwnership: MessageOwnership,
  replyCache: ReplyCache
) extends EventListener:
  private val logger = loggerOf[VoiceRoles]

  private val voiceRoleCommand = new ReplyingCommand with Command.ServerAdminOnly:
    override def name = "voicerole"

    override def aliases: List[String] = List("setvoicerole", "getvoicerole")

    override def description = "Set or query the role automatically assigned to voice chat users"

    override def longDescription(invocation: String): String =
      s"""Usage: `$invocation In Voice` or `$invocation 123456789` (with a role ID)
         |You can also remove the role with `$invocation none`""".stripMargin

    override def executeAndGetMessage(message: Message, args: String): Future[Message] = async {
      await(args.trimnn match
        case ""      => showVoiceRole(message.getGuild)
        case "none"  => delVoiceRole(message.getGuild)
        case trimmed => setVoiceRole(trimmed, message.getGuild)
      ).toMessage
    }.tap(_.failed.foreach(APIHelper.loudFailure("setting voice role", message)))

    private def setVoiceRole(roleName: String, guild: Guild) = async {
      findRole(guild, roleName) match
        case Left(err) => err
        case Right(role) =>
          await(roleByGuild(guild.id) = role.id)
          refreshVoiceRoles(guild)
          BotMessages.okay(s"Set the new voice chat role to ${role.mention}")
    }

    private def delVoiceRole(guild: Guild) = async {
      await(roleByGuild.remove(guild.id))
      refreshVoiceRoles(guild)
      BotMessages.okay(s"Turned off voice chat roles for this server")
    }

    private def showVoiceRole(guild: Guild) = async {
      given JDA = guild.getJDA
      await(roleByGuild.get(guild.id))
        .flatMap(_.find)
        .fold(BotMessages.plain("There is currently no voice chat role set."))(role =>
          BotMessages.okay(s"The voice chat role is currently set to ${role.mention}.")
        )
    }

    override given messageOwnership: MessageOwnership = VoiceRoles.this.messageOwnership

    override given replyCache: ReplyCache = VoiceRoles.this.replyCache
  end voiceRoleCommand

  val allCommands: Seq[Command] = Seq(voiceRoleCommand)

  private def setRole(member: Member, role: Role, shouldHaveRole: Boolean): Unit =
    if shouldHaveRole != member.has(role) then
      {
        if shouldHaveRole then
          logger.debug(s"Adding voice role to user ${member.unambiguousString}")
          member.roles += role -> "voice state change"
        else
          logger.debug(s"Removing voice role from user ${member.unambiguousString}")
          member.roles -= role -> "voice state change"
      }.failed.foreach(APIHelper.failure(s"giving 'in voice' role to ${member.unambiguousString}"))

  private def shouldHaveRole(state: GuildVoiceState) =
    !state.getMember.getUser.isBot && !state.isDeafened && Option(state.getChannel).exists(
      _ != state.getGuild.getAfkChannel
    )

  private val pendingRoleUpdates = ConcurrentHashMap[GuildUserId, ScheduledFuture[Unit]]()
  private val rng = ThreadLocalRandom.current().nn

  private def queueRoleUpdate(member: Member): Unit =
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

      def updateRole(role: Role): Unit =
        pendingRoleUpdates.remove(memberId)
        // TODO: No thread-safe way to do this
        for voiceState <- member.getVoiceState.? do
          setRole(member, role, shouldHaveRole(voiceState))

      def queueUpdate(role: Role): Unit =
        // Delay to ensure that rapid switching of deafen doesn't run our
        // rate limits out.
        val newFuture = scheduler.schedule((200 + rng.nextInt(300)) milliseconds) {
          updateRole(role)
        }
        val previousFuture = pendingRoleUpdates.put(memberId, newFuture).?
        previousFuture.foreach(_.cancel(false))

      given JDA = member.getJDA
      await(roleByGuild.get(member.getGuild.id)).flatMap(_.find).foreach(queueUpdate)
    }

  private def refreshVoiceRoles(guild: Guild): Unit =
    for
      voiceState <- guild.voiceStates
    do queueRoleUpdate(voiceState.getMember)

  override def onEvent(event: GenericEvent): Unit =
    event match
      case ev: ReadyEvent =>
        val jda = ev.getJDA
        async {
          for
            guild <- jda.guilds
          do refreshVoiceRoles(guild)
        }

      case ev: GenericGuildVoiceEvent =>
        queueRoleUpdate(ev.getMember)

      case _ =>
end VoiceRoles

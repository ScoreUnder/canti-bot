package score.discord.canti.functionality.voicekick

import cps.*
import score.discord.canti.util.FutureAsyncMonadButGood
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.managers.Manager
import net.dv8tion.jda.api.requests.ErrorResponse.*
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.{MessageAction, WebhookMessageUpdateAction}
import net.dv8tion.jda.api.{JDA, Permission}
import score.discord.canti.collections.{AsyncMap, ReplyCache}
import score.discord.canti.command.api.{ArgSpec, ArgType, CommandInvocation, CommandPermissions}
import score.discord.canti.command.GenericCommand
import score.discord.canti.discord.permissions.{PermissionAttachment, PermissionCollection}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper.Error
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.collections.AsyncMapConversions.*
import score.discord.canti.wrappers.jda.Conversions.{
  richMessage, richMessageChannel, richUser, richVoiceChannel
}
import score.discord.canti.wrappers.jda.{ID, MessageReceiver, OutgoingMessage, RetrievableMessage}
import score.discord.canti.wrappers.jda.IdConversions.*
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RichGenericComponentInteractionCreateEvent.messageId
import score.discord.canti.wrappers.jda.RichGuild.{findMember, findVoiceChannel}
import score.discord.canti.wrappers.jda.RichGuildChannel.{applyPerms, getPermissionAttachment}
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichSnowflake.id
import score.discord.canti.wrappers.jda.matching.Events.{GuildVoiceUpdate, NonBotReact}
import score.discord.canti.wrappers.jda.matching.React

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Future, blocking}
import scala.jdk.CollectionConverters.*
import scala.language.{implicitConversions, postfixOps}
import scala.util.chaining.*
import net.dv8tion.jda.api.interactions.components.ActionRow

class VoiceKick(
  ownerByChannel: AsyncMap[(ID[Guild], ID[VoiceChannel]), ID[User]],
  voiceBanExpiries: AsyncMap[(ID[Guild], ID[VoiceChannel], ID[User]), VoiceBanExpiry]
)(using MessageOwnership, ReplyCache, Scheduler)
    extends EventListener:

  sealed trait VoteType:
    val emoji: String
    val id: String

  case object KickVote extends VoteType:
    val emoji = "👟"
    val id = "kick"

  case object StayVote extends VoteType:
    val emoji = "📥"
    val id = "stay"

  case object AbstainVote extends VoteType:
    val emoji = "🤷"
    val id = "abstain"

  case class KickState(
    votes: Map[ID[Member], Option[VoteType]],
    target: ID[Member],
    channel: ID[VoiceChannel],
    expiry: Long
  ):
    private def sumVotes(f: VoteType => Int): Int = votes.values.flatten.map(f).sum

    private def hasEnoughUsers = votes.size >= 2

    val passed: Boolean = sumVotes {
      case StayVote    => 0
      case AbstainVote => 1
      case KickVote    => 2
    } > votes.size && hasEnoughUsers

    val failed: Boolean = sumVotes {
      case StayVote    => 2
      case AbstainVote => 1
      case KickVote    => 0
    } >= votes.size || !hasEnoughUsers

    def overallVote: Option[VoteType] =
      if passed then Some(KickVote)
      else if failed then Some(StayVote)
      else None

    def expired: Boolean = System.currentTimeMillis() >= expiry

    def ended: Boolean = passed || failed || expired

  private val pendingKicks = mutable.Map.empty[ID[Message], KickState]
  private val kickMessagesByMember =
    mutable.Map.empty[ID[Member], Set[(ID[TextChannel], ID[Message])]].withDefaultValue(Set.empty)

  object VoiceKickCommand extends GenericCommand:
    override def name: String = "voicekick"

    override def aliases: Seq[String] = Vector("votekick", "vk")

    override def longDescription(invocation: String): String =
      s"""Starts a vote to kick a user from a voice chat, or (if you own the channel) kicks immediately.
         |The user will be temporarily unable to rejoin the channel.
         |Usage: `$invocation @user`
         |""".stripMargin

    override def description: String = "Kicks a user from voice chat"

    override def permissions = CommandPermissions.Anyone

    private val kickUserArg = ArgSpec("user", "The user to kick", ArgType.MentionedUsers)

    override val argSpec = List(kickUserArg)

    override def canBeEdited = false

    override def execute(ctx: CommandInvocation): Future[RetrievableMessage] = async {
      val textChannel = ctx.invoker.channel
      val result = for
        member <- ctx.invoker.member
        guild = member.getGuild
        voiceState <- member.getVoiceState ?<> "Internal error: no voice state cached for you"
        voiceChan <- voiceState.getChannel ?<> "You must be in a voice channel to run this command"

        _ <- Either.cond(
          voiceChan != member.getGuild.getAfkChannel,
          (),
          "You cannot kick a user from the guild AFK channel"
        )

        guildTextChannel <- ensureIsGuildTextChannel(textChannel)

        mentionedUser <- singleMentionedUser(ctx.args(kickUserArg))
        mentioned <- guild.getMember(mentionedUser) ?<> "Cannot find that user in this server"
        mentionedVoiceState <- mentioned.getVoiceState ?<>
          s"Internal error: no voice state cached for ${mentioned.getUser.mentionWithName}"
        mentionedVoiceChan <- mentionedVoiceState.getChannel ?<>
          s"The user ${mentioned.getUser.mentionWithName} is not in voice chat"

        _ <- Either.cond(
          voiceChan == mentionedVoiceChan,
          (),
          s"You are not in the same voice channel as ${mentioned.getUser.mentionWithName}!"
        )
        _ <- Either.cond(mentioned != member, (), "You cannot vote to kick yourself.")
        _ <- Either.cond(
          !voiceState.isDeafened,
          (),
          "You cannot run this command while deafened (i.e. you must be part of the voice chat)"
        )

        voteEligibleUsers = voiceChan.getMembers.asScala.toSeq
          .filter(m => m != mentioned && m.getVoiceState.?.exists(!_.isDeafened))
        usersMissing = voteEligibleUsers.filter(!_.getUser.canSee(guildTextChannel))
        _ <- Either.cond(
          usersMissing.isEmpty,
          (),
          s"Some users cannot see this channel: ${usersMissing.map(_.getUser.mentionWithName).mkString(", ")}"
        )
        _ <- Either.cond(
          voteEligibleUsers.size >= 2,
          (),
          "There are not enough people in the channel to call a vote kick."
        )
      yield
        val votes = voteEligibleUsers.map { mem =>
          mem.id -> (if mem == member then Some(KickVote) else None)
        }.toMap
        val kickState = KickState(
          votes = votes,
          target = mentioned.id,
          channel = voiceChan.id,
          expiry = System.currentTimeMillis() + (10 minutes).toMillis
        )
        val msg = makeMessageContents(kickState, member.getGuild)
        (kickState, guildTextChannel, msg, voiceChan, mentioned)

      val msg =
        result match
          case Left(err) => ctx.invoker.reply(BotMessages.error(err))
          case Right((kickState, guildTextChannel, successMsg, voiceChan, mentioned)) =>
            await(ownerByChannel(voiceChan)) match
              case Some(owner) if owner == ctx.invoker.user =>
                kickVoiceMember(voiceChan, mentioned, textChannel)
                ctx.invoker.reply(
                  BotMessages.okay(
                    s"${mentioned.getAsMention} was forcibly kicked from #${voiceChan.name} by the owner ${owner.getAsMention}"
                  )
                )
              case _ =>
                val msgWithButtons = ctx.invoker.reply(
                  OutgoingMessage(
                    message = successMsg.toMessage,
                    actionRows = Some(List(ActionRow.of(kickVoteComponents*)))
                  )
                )

                val botMsg = await(await(msgWithButtons).retrieve())
                // Record our message ID and initial kick state in pendingKicks
                blocking {
                  pendingKicks.synchronized {
                    pendingKicks += botMsg.id -> kickState
                    for member <- kickState.votes.keys do
                      kickMessagesByMember(member) += ((guildTextChannel.id, botMsg.id))
                  }
                }
                summon[Scheduler].schedule(
                  (0L max (kickState.expiry - System.currentTimeMillis())) milliseconds
                ) {
                  pendingKicks.synchronized {
                    for state <- pendingKicks.get(botMsg.id) do
                      updateVoteKickMessage(botMsg.getTextChannel, state, botMsg.id, None)
                  }
                }

                msgWithButtons
      await(msg)
    }

    private def ensureIsGuildTextChannel(textChannel: MessageChannel): Either[String, TextChannel] =
      textChannel match
        case c: TextChannel => Right(c)
        case _ =>
          Left(
            "Internal error: Command not run from within a guild, but `message.getMember()` disagrees"
          )

    private def singleMentionedUser(mentioned: Seq[User]): Either[String, User] =
      Either.cond(
        mentioned.size == 1,
        mentioned.head,
        if mentioned.isEmpty then "You need to mention a user"
        else "You should mention only one user"
      )
  end VoiceKickCommand

  def allCommands = Seq(VoiceKickCommand)

  private def makeMessageContents(kickState: KickState, guild: Guild) =
    given JDA = guild.getJDA
    extension (me: ID[Member])
      def toStr(f: Member => String): String =
        me.find(guild).map(f).getOrElse("[error: user left server?]")

    val targetMention = kickState.target.toStr(_.getUser.mentionWithName)
    val chanMention = kickState.channel.find.map(_.mention).getOrElse("[error: channel gone?]")
    val usersWhoShouldVote = kickState.votes.keys
      .map(memId => memId.toStr(mem => mem.getUser.mention))
      .mkString(", ")
    val votesSoFar = kickState.votes.values.flatten.map(_.emoji).toVector.sorted.mkString

    val finalResult =
      if kickState.passed then "The vote has passed and the user has been kicked."
      else if kickState.failed then "The vote has failed."
      else if kickState.expired then "The vote has timed out."
      else "The vote is currently in progress."

    if !kickState.ended then
      s"A vote to kick $targetMention from $chanMention has been called.\n" +
        s"$usersWhoShouldVote, please vote for (${KickVote.emoji}) " +
        s"or against (${StayVote.emoji}) the kick, " +
        s"or abstain (${AbstainVote.emoji}) to exclude yourself from the vote.\n\n" +
        s"**Votes**: $votesSoFar\n$finalResult"
    else
      s"A vote to kick $targetMention from $chanMention was called and has concluded.\n$usersWhoShouldVote\n\n" +
        s"**Votes**: $votesSoFar\n$finalResult"

  private def getEmojiMeaning(emoji: String): Option[VoteType] = emoji match
    case KickVote.emoji    => Some(KickVote)
    case AbstainVote.emoji => Some(AbstainVote)
    case StayVote.emoji    => Some(StayVote)
    case _                 => None

  private def getButtonMeaning(id: String): Option[VoteType] = id match
    case KickVote.id    => Some(KickVote)
    case AbstainVote.id => Some(AbstainVote)
    case StayVote.id    => Some(StayVote)
    case _              => None

  private val kickVoteComponents = Seq(
    Button.success(StayVote.id, s"${StayVote.emoji} Stay"),
    Button.secondary(AbstainVote.id, s"${AbstainVote.emoji} Abstain"),
    Button.danger(KickVote.id, s"${KickVote.emoji} Kick"),
  )

  def removeTemporaryVoiceBan(
    voiceChannel: VoiceChannel,
    member: Member,
    logChannel: MessageReceiver,
    explicitGrant: Boolean
  ): Unit =
    for permissionOverride <- voiceChannel.getPermissionOverride(member).? do
      val originalPerms = PermissionAttachment(permissionOverride)
      val permsWithoutVoiceBan = originalPerms.clear(Permission.VOICE_CONNECT)

      APIHelper.tryRequest(
        {
          if explicitGrant then permissionOverride.getManager.grant(Permission.VOICE_CONNECT)
          else if permsWithoutVoiceBan.isEmpty then permissionOverride.delete()
          else permissionOverride.getManager.clear(Permission.VOICE_CONNECT)
        },
        onFail = {
          case Error(
                UNKNOWN_OVERRIDE | UNKNOWN_CHANNEL
              ) => // Ignore (ban is no longer applicable anyway)
          case throwable =>
            APIHelper.loudFailure(
              s"undoing voice tempban permissions in ${voiceChannel.mention} for ${member.getUser.mention}",
              logChannel
            )(throwable)
        }
      )

    // Remove voice ban expiry info from DB (no longer necessary)
    voiceBanExpiries.remove(voiceBanKey(voiceChannel, member))

  private def voiceBanKey(
    channel: VoiceChannel,
    member: Member
  ): (ID[Guild], ID[VoiceChannel], ID[User]) =
    (channel.getGuild.id, channel.id, member.getUser.id)

  def addTemporaryVoiceBan(
    voiceChannel: VoiceChannel,
    member: Member,
    logChannel: MessageReceiver
  ): Unit =
    val originalPerms = voiceChannel.getPermissionAttachment(member)

    if !originalPerms.denies.contains(Permission.VOICE_CONNECT) then
      val permsWithVoiceBan = originalPerms.deny(Permission.VOICE_CONNECT)
      val futureReq = APIHelper.tryRequest(
        {
          // XXX Oh my god static mutable globals in a multithreaded environment
          // XXX Hack: JDA seems to consistently get the wrong idea about permissions here for some reason.
          Manager.setPermissionChecksEnabled(false)
          try voiceChannel.applyPerms(PermissionCollection(Seq(member -> permsWithVoiceBan)))
          finally Manager.setPermissionChecksEnabled(true)
        },
        onFail = APIHelper.loudFailure(
          s"adding voice tempban permissions to ${voiceChannel.mention} for ${member.getUser.mention}",
          logChannel
        )
      )

      // Whether we need to explicitly grant the permission back
      val explicitGrant = originalPerms.allows.contains(Permission.VOICE_CONNECT)

      for _ <- futureReq do
        // Take note (in DB) of when the voice ban should expire
        val expiryTimestamp = System.currentTimeMillis() + (10 minutes).toMillis
        voiceBanExpiries(voiceBanKey(voiceChannel, member)) =
          VoiceBanExpiry(expiryTimestamp, explicitGrant)

        // Expire voice ban using scheduler
        summon[Scheduler].schedule(10 minutes) {
          removeTemporaryVoiceBan(voiceChannel, member, logChannel, explicitGrant)
        }

  def kickVoiceMember(
    voiceChannel: VoiceChannel,
    member: Member,
    logChannel: MessageChannel
  ): Unit =
    for
      voiceState <- member.getVoiceState.?
      if voiceState.getChannel == voiceChannel
    do
      APIHelper.tryRequest(
        voiceChannel.getGuild.kickVoiceMember(member),
        onFail = APIHelper.loudFailure(
          s"kicking ${member.getUser.mention} from voice chat",
          MessageReceiver(logChannel)
        )
      )

  private def completeKick(
    channel: TextChannel,
    kickState: KickState,
    myMessage: ID[Message],
    deferredEdit: Option[Future[InteractionHook]]
  ): Unit =
    val maybeResults = for
      member <- kickState.target
        .find(channel.getGuild)
        .toRight("Could not find the member to kick them from the channel")
      voiceChannel <- channel.getGuild
        .findVoiceChannel(kickState.channel)
        .toRight("Could not find the voice channel to kick from")
    yield (member, voiceChannel)

    maybeResults match
      case Left(err) => channel ! BotMessages.error(err)
      case Right((member, voiceChannel)) =>
        addTemporaryVoiceBan(voiceChannel, member, MessageReceiver(channel))
        kickVoiceMember(voiceChannel, member, channel)
        updateVoteKickMessage(channel, kickState, myMessage, deferredEdit)
        channel ! BotMessages.plain(
          s"The vote has passed and ${member.getUser.mention} will be kicked from ${voiceChannel.mention}"
        )

  def updateVoteKickMessage(
    channel: TextChannel,
    kickState: KickState,
    myMessage: ID[Message],
    deferredEdit: Option[Future[InteractionHook]]
  ): Unit =
    def tryEdit[A <: RestAction[Message]](editAction: String => A, clearActionRows: A => A) =
      APIHelper.tryRequest(
        {
          val msg = editAction(makeMessageContents(kickState, channel.getGuild))
          if kickState.ended then clearActionRows(msg) else msg
        },
        onFail = APIHelper.failure("editing voice kick results")
      )

    deferredEdit match
      case Some(deferredEdit) =>
        deferredEdit.flatMap(deferredEdit => tryEdit(deferredEdit.editOriginal, _.setActionRows()))
      case None =>
        tryEdit(channel.editMessageById(myMessage.value, _), _.setActionRows())

    if kickState.ended then removePendingKick(channel, myMessage)

  private def updateKickVote(
    channel: TextChannel,
    myMessage: ID[Message],
    voteType: VoteType,
    member: Member,
    deferredEdit: Option[Future[InteractionHook]]
  ): Future[Option[KickState]] =
    Future {
      blocking {
        pendingKicks.synchronized {
          pendingKicks
            .get(myMessage)
            // ensure the vote comes from an eligible voter
            .filter { kickState => kickState.votes.contains(member.id) && !kickState.expired }
            .map { kickState =>
              val newVotes = kickState.votes + (member.id -> Some(voteType))
              val newKickState = kickState.copy(votes = newVotes)

              val result = newKickState.overallVote

              if result.isEmpty then pendingKicks(myMessage) = newKickState
              else removePendingKick(channel, myMessage)

              newKickState
            }
        }
      }.tap(_.foreach {
        afterUpdateKickState(channel, myMessage, deferredEdit, _)
      })
    }

  private def removePendingKick(channel: TextChannel, myMessage: ID[Message]) =
    pendingKicks.synchronized {
      pendingKicks.remove(myMessage).tap { kickStateMaybe =>
        for kickState <- kickStateMaybe; member <- kickState.votes.keys do
          kickMessagesByMember(member) -= ((channel.id, myMessage))
      }
    }

  private def afterUpdateKickState(
    channel: TextChannel,
    myMessage: ID[Message],
    deferredEdit: Option[Future[InteractionHook]],
    kickState: KickState
  ): Unit =
    kickState.overallVote match
      case Some(KickVote) => completeKick(channel, kickState, myMessage, deferredEdit)
      case _              => updateVoteKickMessage(channel, kickState, myMessage, deferredEdit)

  private def removeUserFromVote(member: Member): Unit = Future {
    given JDA = member.getJDA
    blocking {
      pendingKicks.synchronized {
        val oldMessages = kickMessagesByMember(member.id)

        // Remove user from all the votes they are taking part in
        for (_, message) <- oldMessages do
          val kickState = pendingKicks(message)
          pendingKicks(message) = kickState.copy(votes = kickState.votes - member.id)
        kickMessagesByMember(member.id) = Set.empty

        // Take note of kick votes that need updating now that a user has dropped out
        oldMessages.view.map(tup => (tup, pendingKicks(tup._2))).toSeq
      }
    }.foreach { case ((textChannelId, messageId), kickState) =>
      textChannelId.find.foreach { textChannel =>
        afterUpdateKickState(textChannel, messageId, None, kickState)
      }
    }
  }

  override def onEvent(event: GenericEvent): Unit = event match
    case _: ReadyEvent =>
      given JDA = event.getJDA
      for
        expiries <- voiceBanExpiries.items
        ((guildId, channelId, userId), VoiceBanExpiry(expiryTime, explicitGrant)) <- expiries
      do
        val scheduledUnban = for
          guild <- guildId.find
          channel <- channelId.find
          user <- userId.find
          member <- guild.findMember(user)
        yield summon[Scheduler].schedule(
          (0L max (expiryTime - System.currentTimeMillis())) milliseconds
        ) {
          removeTemporaryVoiceBan(channel, member, MessageReceiver.NullReceiver, explicitGrant)
        }

        if scheduledUnban.isEmpty then
          // One of the entities (e.g. voice channel) no longer exists, so not applicable
          voiceBanExpiries.remove((guildId, channelId, userId))
    case NonBotReact(React.Text(emoji), msgId, channel: TextChannel, user) =>
      for
        vote <- getEmojiMeaning(emoji)
        member <- channel.getGuild.getMember(user).?
      do updateKickVote(channel, msgId, vote, member, None)
    // TODO: Handle deletion of message
    case ev: ButtonClickEvent =>
      for
        vote <- getButtonMeaning(ev.getComponentId)
        member <- ev.getMember.?
        channel = ev.getTextChannel
        msgId = ev.messageId
      do
        val edit = ev.deferEdit().queueFuture()
        updateKickVote(channel, msgId, vote, member, Some(edit))
    case GuildVoiceUpdate(member, Some(_), _) =>
      removeUserFromVote(member)
    case _ =>
end VoiceKick

package score.discord.canti.functionality.voicekick

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.managers.Manager
import net.dv8tion.jda.api.requests.ErrorResponse.*
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.{JDA, Permission}
import score.discord.canti.collections.{AsyncMap, ReplyCache}
import score.discord.canti.command.api.{ArgSpec, ArgType, CommandInvocation, CommandPermissions}
import score.discord.canti.command.GenericCommand
import score.discord.canti.discord.permissions.PermissionHolder.asPermissionHolder
import score.discord.canti.discord.permissions.{
  PermissionAttachment, PermissionCollection, PermissionValue
}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper.Error
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.collections.AsyncMapConversions.*
import score.discord.canti.wrappers.jda.Conversions.{
  richChannel, richGuildChannel, richMessage, richMessageChannel, richUser
}
import score.discord.canti.wrappers.jda.{ID, MessageReceiver, RetrievableMessage}
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
import scala.util.Try
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.utils.messages.MessageEditRequest
import net.dv8tion.jda.api.events.session.ReadyEvent

class VoiceKick(
  ownerByChannel: AsyncMap[(ID[Guild], ID[AudioChannel]), ID[User]],
  voiceBanExpiries: AsyncMap[(ID[Guild], ID[AudioChannel], ID[User]), VoiceBanExpiry]
)(using MessageOwnership, ReplyCache, Scheduler)
    extends EventListener:

  enum VoteType(val emoji: String, val id: String):
    case Kick extends VoteType("👟", "kick")
    case Stay extends VoteType("📥", "stay")
    case Abstain extends VoteType("🤷", "abstain")

  case class KickState(
    votes: Map[ID[Member], Option[VoteType]],
    target: ID[Member],
    channel: ID[AudioChannel],
    expiry: Long
  ):
    private def sumVotes(f: VoteType => Int): Int = votes.values.flatten.map(f).sum

    private def hasEnoughUsers = votes.size >= 2

    val passed: Boolean = sumVotes {
      case VoteType.Stay    => 0
      case VoteType.Abstain => 1
      case VoteType.Kick    => 2
    } > votes.size && hasEnoughUsers

    val failed: Boolean = sumVotes {
      case VoteType.Stay    => 2
      case VoteType.Abstain => 1
      case VoteType.Kick    => 0
    } >= votes.size || !hasEnoughUsers

    def overallVote: Option[VoteType] =
      if passed then Some(VoteType.Kick)
      else if failed then Some(VoteType.Stay)
      else None

    def expired: Boolean = System.currentTimeMillis() >= expiry

    def ended: Boolean = passed || failed || expired

  private val pendingKicks = mutable.Map.empty[ID[Message], KickState]
  private val kickMessagesByMember =
    mutable.Map
      .empty[ID[Member], Set[(ID[MessageChannel], ID[Message])]]
      .withDefaultValue(Set.empty)

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
      val result = for
        member <- ctx.invoker.member
        guild = member.getGuild.nn
        voiceState <- member.getVoiceState ?<> "Internal error: no voice state cached for you"
        audioChan <- voiceState.getChannel ?<> "You must be in a voice channel to run this command"

        _ <- Either.cond(
          audioChan != member.getGuild.nn.getAfkChannel,
          (),
          "You cannot kick a user from the guild AFK channel"
        )

        guildTextChannel <- ensureIsGuildTextChannel(ctx.invoker.channel)

        mentionedUser <- singleMentionedUser(ctx.args(kickUserArg))
        mentioned <- guild.getMember(mentionedUser) ?<> "Cannot find that user in this server"
        mentionedVoiceState <- mentioned.getVoiceState ?<>
          s"Internal error: no voice state cached for ${mentioned.getUser.nn.mentionWithName}"
        mentionedVoiceChan <- mentionedVoiceState.getChannel ?<>
          s"The user ${mentioned.getUser.nn.mentionWithName} is not in voice chat"
        voiceChan <- Try(audioChan.asVoiceChannel.nn).toOption
          .toRight(
            "This command can only be used with voice channels"
          ) // as opposed to stage channels

        _ <- Either.cond(
          voiceChan == mentionedVoiceChan,
          (),
          s"You are not in the same voice channel as ${mentioned.getUser.nn.mentionWithName}!"
        )
        _ <- Either.cond(mentioned != member, (), "You cannot vote to kick yourself.")
        _ <- Either.cond(
          !voiceState.isDeafened,
          (),
          "You cannot run this command while deafened (i.e. you must be part of the voice chat)"
        )

        voteEligibleUsers = voiceChan.getMembers.nn.asScala.toSeq
          .filter(m => m != mentioned && m.getVoiceState.?.exists(!_.isDeafened))
        usersMissing = voteEligibleUsers.filter(!_.getUser.nn.canSee(guildTextChannel))
        _ <- Either.cond(
          usersMissing.isEmpty,
          (),
          s"Some users cannot see this channel: ${usersMissing.map(_.getUser.nn.mentionWithName).mkString(", ")}"
        )
        _ <- Either.cond(
          voteEligibleUsers.size >= 2,
          (),
          "There are not enough people in the channel to call a vote kick."
        )
      yield
        val votes = voteEligibleUsers.map { mem =>
          mem.id -> (if mem == member then Some(VoteType.Kick) else None)
        }.toMap
        val kickState = KickState(
          votes = votes,
          target = mentioned.id,
          channel = voiceChan.id,
          expiry = System.currentTimeMillis() + (10 minutes).toMillis
        )
        val msg = makeMessageContents(kickState, member.getGuild.nn)
        (kickState, guildTextChannel, msg, voiceChan, mentioned)

      val msg =
        result match
          case Left(err) => ctx.invoker.reply(BotMessages.error(err))
          case Right((kickState, guildTextChannel, successMsg, voiceChan, mentioned)) =>
            await(ownerByChannel(voiceChan)) match
              case Some(owner) if owner == ctx.invoker.user.id =>
                addTemporaryVoiceBan(voiceChan, mentioned, MessageReceiver(guildTextChannel))
                kickVoiceMember(voiceChan, mentioned, guildTextChannel)
                ctx.invoker.reply(
                  BotMessages.okay(
                    s"${mentioned.getAsMention} was forcibly kicked from #${voiceChan.name} by the owner ${ctx.invoker.user.getAsMention}"
                  )
                )
              case _ =>
                val msgWithButtons = ctx.invoker.reply(
                  MessageCreateBuilder()
                    .setContent(successMsg)
                    .nn
                    .setComponents(ActionRow.of(kickVoteComponents*))
                    .nn
                    .build
                    .nn
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
                      updateVoteKickMessage(guildTextChannel, state, botMsg.id, None)
                  }
                }

                msgWithButtons
      await(msg)
    }

    private def ensureIsGuildTextChannel(
      textChannel: Option[MessageChannel]
    ): Either[String, GuildMessageChannel] =
      textChannel match
        case Some(c: GuildMessageChannel) => Right(c)
        case None                         => Left("Cannot determine current channel")
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
    given JDA = guild.getJDA.nn
    extension (me: ID[Member])
      def toStr(f: Member => String): String =
        me.find(guild).fold("[error: user left server?]")(f)

    val targetMention = kickState.target.toStr(_.getUser.nn.mentionWithName)
    // TODO: support stage channels?
    val chanMention = kickState.channel
      .asInstanceOf[ID[VoiceChannel]]
      .find
      .fold("[error: channel gone?]")(_.mention)
    val usersWhoShouldVote = kickState.votes.keys
      .map(memId => memId.toStr(mem => mem.getUser.nn.mention))
      .mkString(", ")
    val votesSoFar = kickState.votes.values.flatten.map(_.emoji).toVector.sorted.mkString

    val finalResult =
      if kickState.passed then "The vote has passed and the user has been kicked."
      else if kickState.failed then "The vote has failed."
      else if kickState.expired then "The vote has timed out."
      else "The vote is currently in progress."

    if !kickState.ended then
      s"A vote to kick $targetMention from $chanMention has been called.\n" +
        s"$usersWhoShouldVote, please vote for (${VoteType.Kick.emoji}) " +
        s"or against (${VoteType.Stay.emoji}) the kick, " +
        s"or abstain (${VoteType.Abstain.emoji}) to exclude yourself from the vote.\n\n" +
        s"**Votes**: $votesSoFar\n$finalResult"
    else
      s"A vote to kick $targetMention from $chanMention was called and has concluded.\n$usersWhoShouldVote\n\n" +
        s"**Votes**: $votesSoFar\n$finalResult"

  private def getEmojiMeaning(emoji: String): Option[VoteType] = emoji match
    case VoteType.Kick.emoji    => Some(VoteType.Kick)
    case VoteType.Abstain.emoji => Some(VoteType.Abstain)
    case VoteType.Stay.emoji    => Some(VoteType.Stay)
    case _                      => None

  private def getButtonMeaning(id: String): Option[VoteType] = id match
    case VoteType.Kick.id    => Some(VoteType.Kick)
    case VoteType.Abstain.id => Some(VoteType.Abstain)
    case VoteType.Stay.id    => Some(VoteType.Stay)
    case _                   => None

  private val kickVoteComponents = Seq(
    Button.success(VoteType.Stay.id, s"${VoteType.Stay.emoji} Stay"),
    Button.secondary(VoteType.Abstain.id, s"${VoteType.Abstain.emoji} Abstain"),
    Button.danger(VoteType.Kick.id, s"${VoteType.Kick.emoji} Kick"),
  )

  def removeTemporaryVoiceBan(
    audioChannel: AudioChannel,
    member: Member,
    logChannel: MessageReceiver,
    explicitGrant: Boolean
  ): Unit =
    for permissionOverride <- audioChannel.getPermissionOverride(member).? do
      val originalPerms = PermissionAttachment(permissionOverride)
      val permsWithoutVoiceBan = originalPerms.clear(Set(Permission.VOICE_CONNECT))

      APIHelper.tryRequest(
        {
          if explicitGrant then permissionOverride.getManager.nn.grant(Permission.VOICE_CONNECT).nn
          else if permsWithoutVoiceBan.isEmpty then permissionOverride.delete().nn
          else permissionOverride.getManager.nn.clear(Permission.VOICE_CONNECT).nn
        },
        onFail = {
          case Error(
                UNKNOWN_OVERRIDE | UNKNOWN_CHANNEL
              ) => // Ignore (ban is no longer applicable anyway)
          case throwable =>
            APIHelper.loudFailure(
              s"undoing voice tempban permissions in ${audioChannel.mention} for ${member.getUser.nn.mention}",
              logChannel
            )(throwable)
        }
      )

    // Remove voice ban expiry info from DB (no longer necessary)
    voiceBanExpiries.remove(voiceBanKey(audioChannel, member))

  private def voiceBanKey(
    channel: AudioChannel,
    member: Member
  ): (ID[Guild], ID[AudioChannel], ID[User]) =
    (channel.getGuild.nn.id, channel.id, member.getUser.nn.id)

  def addTemporaryVoiceBan(
    audioChannel: AudioChannel,
    member: Member,
    logChannel: MessageReceiver
  ): Unit =
    val originalPerms = audioChannel.getPermissionAttachment(member)

    if originalPerms.get(Permission.VOICE_CONNECT) != PermissionValue.Deny then
      val permsWithVoiceBan = originalPerms.deny(Set(Permission.VOICE_CONNECT))
      val futureReq = APIHelper.tryRequest(
        {
          // XXX Oh my god static mutable globals in a multithreaded environment
          // XXX Hack: JDA seems to consistently get the wrong idea about permissions here for some reason.
          Manager.setPermissionChecksEnabled(false)
          try
            audioChannel.applyPerms(
              PermissionCollection(member.asPermissionHolder -> permsWithVoiceBan)
            )
          finally Manager.setPermissionChecksEnabled(true)
        },
        onFail = APIHelper.loudFailure(
          s"adding voice tempban permissions to ${audioChannel.mention} for ${member.getUser.nn.mention}",
          logChannel
        )
      )

      // Whether we need to explicitly grant the permission back
      val explicitGrant = originalPerms.get(Permission.VOICE_CONNECT) == PermissionValue.Allow

      for _ <- futureReq do
        // Take note (in DB) of when the voice ban should expire
        val expiryTimestamp = System.currentTimeMillis() + (10 minutes).toMillis
        voiceBanExpiries(voiceBanKey(audioChannel, member)) =
          VoiceBanExpiry(expiryTimestamp, explicitGrant)

        // Expire voice ban using scheduler
        summon[Scheduler].schedule(10 minutes) {
          removeTemporaryVoiceBan(audioChannel, member, logChannel, explicitGrant)
        }

  def kickVoiceMember(
    audioChannel: AudioChannel,
    member: Member,
    logChannel: MessageChannel
  ): Unit =
    for
      voiceState <- member.getVoiceState.?
      if voiceState.getChannel == audioChannel
    do
      APIHelper.tryRequest(
        audioChannel.getGuild.nn.kickVoiceMember(member).nn,
        onFail = APIHelper.loudFailure(
          s"kicking ${member.getUser.nn.mention} from voice chat",
          MessageReceiver(logChannel)
        )
      )

  private def completeKick(
    channel: GuildMessageChannel,
    kickState: KickState,
    myMessage: ID[Message],
    deferredEdit: Option[Future[InteractionHook]]
  ): Unit =
    val guild = channel.getGuild.nn
    val maybeResults = for
      member <- kickState.target
        .find(guild)
        .toRight("Could not find the member to kick them from the channel")
      voiceChannel <- guild
        // TODO: support stage channels?
        .findVoiceChannel(kickState.channel.asInstanceOf[ID[VoiceChannel]])
        .toRight("Could not find the voice channel to kick from")
    yield (member, voiceChannel)

    maybeResults match
      case Left(err) => channel ! BotMessages.error(err)
      case Right((member, voiceChannel)) =>
        addTemporaryVoiceBan(voiceChannel, member, MessageReceiver(channel))
        kickVoiceMember(voiceChannel, member, channel)
        updateVoteKickMessage(channel, kickState, myMessage, deferredEdit)
        channel ! BotMessages.plain(
          s"The vote has passed and ${member.getUser.nn.mention} will be kicked from ${voiceChannel.mention}"
        )

  def updateVoteKickMessage(
    channel: GuildMessageChannel,
    kickState: KickState,
    myMessage: ID[Message],
    deferredEdit: Option[Future[InteractionHook]]
  ): Unit =
    def tryEdit[T <: MessageEditRequest[T] & RestAction[?]](editAction: String => T) =
      APIHelper.tryRequest(
        {
          val msg = editAction(makeMessageContents(kickState, channel.getGuild.nn))
          if kickState.ended then msg.setComponents().nn else msg
        },
        onFail = APIHelper.failure("editing voice kick results")
      )

    deferredEdit match
      case Some(deferredEdit) =>
        deferredEdit.flatMap(deferredEdit => tryEdit(m => deferredEdit.editOriginal(m).nn))
      case None =>
        tryEdit(m => channel.editMessageById(myMessage.value, m).nn)

    if kickState.ended then removePendingKick(channel, myMessage)

  private def updateKickVote(
    channel: GuildMessageChannel,
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

  private def removePendingKick(channel: MessageChannel, myMessage: ID[Message]) =
    pendingKicks.synchronized {
      pendingKicks.remove(myMessage).tap { kickStateMaybe =>
        for kickState <- kickStateMaybe; member <- kickState.votes.keys do
          kickMessagesByMember(member) -= ((channel.id, myMessage))
      }
    }

  private def afterUpdateKickState(
    channel: GuildMessageChannel,
    myMessage: ID[Message],
    deferredEdit: Option[Future[InteractionHook]],
    kickState: KickState
  ): Unit =
    kickState.overallVote match
      case Some(VoteType.Kick) => completeKick(channel, kickState, myMessage, deferredEdit)
      case _                   => updateVoteKickMessage(channel, kickState, myMessage, deferredEdit)

  private def removeUserFromVote(member: Member): Unit = Future {
    given JDA = member.getJDA.nn
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
      textChannelId.find.foreach { case textChannel: GuildMessageChannel =>
        afterUpdateKickState(textChannel, messageId, None, kickState)

      }
    }
  }

  override def onEvent(event: GenericEvent): Unit = event match
    case _: ReadyEvent =>
      given JDA = event.getJDA.nn
      for
        expiries <- voiceBanExpiries.items
        ((guildId, channelId, userId), VoiceBanExpiry(expiryTime, explicitGrant)) <- expiries
      do
        val scheduledUnban = for
          guild <- guildId.find
          // TODO: support stage channels?
          channel <- channelId.asInstanceOf[ID[VoiceChannel]].find
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
    case NonBotReact(React.Text(emoji), msgId, channel: GuildMessageChannel, user) =>
      for
        vote <- getEmojiMeaning(emoji)
        member <- channel.getGuild.nn.getMember(user).?
      do updateKickVote(channel, msgId, vote, member, None)
    // TODO: Handle deletion of message
    case ev: ButtonInteractionEvent =>
      for
        vote <- getButtonMeaning(ev.getComponentId.nn)
        member <- ev.getMember.?
        channel = ev.getChannel.nn
        msgId = ev.messageId
      do
        channel match
          case channel: GuildMessageChannel =>
            val edit = ev.deferEdit().nn.queueFuture()
            updateKickVote(channel, msgId, vote, member, Some(edit))
          case _ =>
            APIHelper.tryRequest(
              ev.reply("This command can only be used within a guild").nn,
              onFail = APIHelper.failure("replying with error to button click")
            )
    case GuildVoiceUpdate(member, Some(_), _) =>
      removeUserFromVote(member)
    case _ =>
end VoiceKick

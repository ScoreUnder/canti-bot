package score.discord.canti.functionality.voicekick

import net.dv8tion.jda.api.entities._
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.managers.Manager
import net.dv8tion.jda.api.requests.ErrorResponse._
import net.dv8tion.jda.api.{JDA, Permission}
import score.discord.canti.collections.{AsyncMap, ReplyCache}
import score.discord.canti.command.Command
import score.discord.canti.discord.permissions.{PermissionAttachment, PermissionCollection}
import score.discord.canti.functionality.Commands
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper.Error
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.collections.AsyncMapConversions._
import score.discord.canti.wrappers.jda.Conversions._
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.IdConversions._
import score.discord.canti.wrappers.jda.matching.Events.{GuildVoiceUpdate, NonBotReact}
import score.discord.canti.wrappers.jda.matching.React

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, blocking}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.chaining._

class VoiceKick(ownerByChannel: AsyncMap[(ID[Guild], ID[VoiceChannel]), ID[User]],
                voiceBanExpiries: AsyncMap[(ID[Guild], ID[VoiceChannel], ID[User]), VoiceBanExpiry])
               (implicit messageOwnership: MessageOwnership, replyCache: ReplyCache, scheduler: Scheduler) extends EventListener {

  sealed trait VoteType {
    val emoji: String
  }

  case object KickVote extends VoteType {
    val emoji = "👟"
  }

  case object StayVote extends VoteType {
    val emoji = "📥"
  }

  case object AbstainVote extends VoteType {
    val emoji = "🤷"
  }

  case class KickState(votes: Map[ID[Member], Option[VoteType]], target: ID[Member], channel: ID[VoiceChannel], expiry: Long) {
    private def sumVotes(f: VoteType => Int): Int = votes.values.flatten.map(f).sum

    private def hasEnoughUsers = votes.size >= 2

    val passed: Boolean = sumVotes {
      case StayVote => 0
      case AbstainVote => 1
      case KickVote => 2
    } > votes.size && hasEnoughUsers

    val failed: Boolean = sumVotes {
      case StayVote => 2
      case AbstainVote => 1
      case KickVote => 0
    } >= votes.size || !hasEnoughUsers

    def overallVote: Option[VoteType] =
      if (passed) Some(KickVote)
      else if (failed) Some(StayVote)
      else None

    def expired: Boolean = System.currentTimeMillis() >= expiry

    def ended: Boolean = passed || failed || expired
  }

  private val pendingKicks = mutable.Map.empty[ID[Message], KickState]
  private val kickMessagesByMember = mutable.Map.empty[ID[Member], Set[(ID[TextChannel], ID[Message])]].withDefaultValue(Set.empty)

  def registerCommands(commands: Commands): Unit = {
    commands register new Command.Anyone {
      override def name: String = "voicekick"

      override def aliases: Seq[String] = Vector("votekick", "vk")

      override def longDescription(invocation: String): String =
        s"""Starts a vote to kick a user from a voice chat, or (if you own the channel) kicks immediately.
           |The user will be temporarily unable to rejoin the channel.
           |Usage: `$invocation @user`
           |""".stripMargin

      override def description: String = "Kicks a user from voice chat"

      override def execute(message: Message, args: String): Unit = Future {
        val textChannel = message.getChannel
        val result = for {
          member <- Option(message.getMember).toRight("You must run this command in a public server")
          voiceState <- Option(member.getVoiceState).toRight("Internal error: no voice state cached for you")
          voiceChan <- Option(voiceState.getChannel).toRight("You must be in a voice channel to run this command")

          _ <- Either.cond(voiceChan != member.getGuild.getAfkChannel, (),
            "You cannot kick a user from the guild AFK channel")

          guildTextChannel <- ensureIsGuildTextChannel(textChannel)

          mentioned <- singleMentionedMember(message)
          mentionedVoiceState <- Option(mentioned.getVoiceState)
            .toRight(s"Internal error: no voice state cached for ${mentioned.getUser.mentionWithName}")
          mentionedVoiceChan <- Option(mentionedVoiceState.getChannel)
            .toRight(s"The user ${mentioned.getUser.mentionWithName} is not in voice chat")

          _ <- Either.cond(voiceChan == mentionedVoiceChan, (),
            s"You are not in the same voice channel as ${mentioned.getUser.mentionWithName}!")
          _ <- Either.cond(mentioned != member, (), "You cannot vote to kick yourself.")
          _ <- Either.cond(!voiceState.isDeafened, (),
            "You cannot run this command while deafened (i.e. you must be part of the voice chat)")

          voteEligibleUsers = voiceChan.getMembers.asScala.toSeq
            .filter(m => m != mentioned && Option(m.getVoiceState).exists(!_.isDeafened))
          usersMissing = voteEligibleUsers.filter(!_.getUser.canSee(guildTextChannel))
          _ <- Either.cond(usersMissing.isEmpty, (),
            s"Some users cannot see this channel: ${usersMissing.map(_.getUser.mentionWithName).mkString(", ")}")
          _ <- Either.cond(voteEligibleUsers.size >= 2, (),
            "There are not enough people in the channel to call a vote kick.")
        } yield {
          val votes = voteEligibleUsers.map { mem =>
            mem.id -> (if (mem == member) Some(KickVote) else None)
          }.toMap
          val kickState = KickState(
            votes = votes,
            target = mentioned.id,
            channel = voiceChan.id,
            expiry = System.currentTimeMillis() + (10 minutes).toMillis)
          val msg = makeMessageContents(kickState, member.getGuild)
          (kickState, guildTextChannel, msg, voiceChan, mentioned)
        }

        result.left.foreach { err => message ! BotMessages.error(err) }

        for (resultRight <- result;
             (kickState, guildTextChannel, successMsg, voiceChan, mentioned) = resultRight;
             ownerOption <- ownerByChannel(voiceChan)) {

          ownerOption match {
            case Some(owner) if owner == message.getAuthor =>
              kickVoiceMember(voiceChan, mentioned, textChannel)
              message ! BotMessages.okay(
                s"${mentioned.getAsMention} was forcibly kicked from #${voiceChan.name} by the owner ${owner.getAsMention}")
            case _ =>
              for (botMsg <- message ! successMsg) {
                // Record our message ID and initial kick state in pendingKicks
                blocking {
                  pendingKicks.synchronized {
                    pendingKicks += botMsg.id -> kickState
                    for (member <- kickState.votes.keys) {
                      kickMessagesByMember(member) += ((guildTextChannel.id, botMsg.id))
                    }
                  }
                }
                botMsg.addReaction(KickVote.emoji).queue()
                botMsg.addReaction(AbstainVote.emoji).queue()
                botMsg.addReaction(StayVote.emoji).queue()
                scheduler.schedule((0L max (kickState.expiry - System.currentTimeMillis())) milliseconds) {
                  pendingKicks.synchronized {
                    for (state <- pendingKicks.get(botMsg.id))
                      updateVoteKickMessage(botMsg.getTextChannel, state, botMsg.id)
                  }
                }
              }
          }
        }
      }

      private def ensureIsGuildTextChannel(textChannel: MessageChannel): Either[String, TextChannel] =
        textChannel match {
          case c: TextChannel => Right(c)
          case _ => Left("Internal error: Command not run from within a guild, but `message.getMember()` disagrees")
        }

      private def singleMentionedMember(message: Message): Either[String, Member] = {
        val mentioned = message.getMentionedMembers
        if (mentioned.size == 0) Left("You need to mention a user")
        else if (mentioned.size > 1) Left("You should mention only one user")
        else Right(mentioned.get(0))
      }
    }
  }

  private def makeMessageContents(kickState: KickState, guild: Guild) = {
    implicit val jda: JDA = guild.getJDA
    implicit class RichMemId(me: ID[Member]) {
      def toStr(f: Member => String): String =
        me.find(guild).map(f).getOrElse("[error: user left server?]")
    }

    val targetMention = kickState.target.toStr(_.getUser.mentionWithName)
    val chanMention = kickState.channel.find.map(_.mention).getOrElse("[error: channel gone?]")
    val usersWhoShouldVote = kickState.votes
      .keys
      .map(memId => memId.toStr(mem => mem.getUser.mention))
      .mkString(", ")
    val votesSoFar = kickState.votes.values.flatten.map(_.emoji).toVector.sorted.mkString

    val finalResult =
      if (kickState.passed) "The vote has passed and the user has been kicked."
      else if (kickState.failed) "The vote has failed."
      else if (kickState.expired) "The vote has timed out."
      else "The vote is currently in progress."

    if (!kickState.ended)
      s"A vote to kick $targetMention from $chanMention has been called.\n" +
        s"$usersWhoShouldVote, please vote for (${KickVote.emoji}) " +
        s"or against (${StayVote.emoji}) the kick, " +
        s"or abstain (${AbstainVote.emoji}) to exclude yourself from the vote.\n\n" +
        s"**Votes**: $votesSoFar\n$finalResult"
    else s"A vote to kick $targetMention from $chanMention was called and has concluded.\n$usersWhoShouldVote\n\n" +
      s"**Votes**: $votesSoFar\n$finalResult"
  }

  private def getEmojiMeaning(emoji: String): Option[VoteType] = emoji match {
    case KickVote.emoji => Some(KickVote)
    case AbstainVote.emoji => Some(AbstainVote)
    case StayVote.emoji => Some(StayVote)
    case _ => None
  }

  def removeTemporaryVoiceBan(voiceChannel: VoiceChannel, member: Member, logChannel: Option[MessageChannel], explicitGrant: Boolean): Unit = {
    Option(voiceChannel.getPermissionOverride(member)).foreach { permissionOverride =>
      val originalPerms = PermissionAttachment(permissionOverride)
      val permsWithoutVoiceBan = originalPerms.clear(Permission.VOICE_CONNECT)

      APIHelper.tryRequest({
        if (explicitGrant) {
          permissionOverride.getManager.grant(Permission.VOICE_CONNECT)
        } else if (permsWithoutVoiceBan.isEmpty) {
          permissionOverride.delete()
        } else {
          permissionOverride.getManager.clear(Permission.VOICE_CONNECT)
        }
      }, onFail = {
        case Error(UNKNOWN_OVERRIDE | UNKNOWN_CHANNEL) => // Ignore (ban is no longer applicable anyway)
        case throwable =>
          APIHelper.loudFailure(s"undoing voice tempban permissions in ${voiceChannel.mention} for ${member.getUser.mention}", logChannel)(throwable)
      })
    }

    // Remove voice ban expiry info from DB (no longer necessary)
    voiceBanExpiries.remove(voiceBanKey(voiceChannel, member))
  }

  private def voiceBanKey(channel: VoiceChannel, member: Member) =
    (channel.getGuild.id, channel.id, member.getUser.id)

  def addTemporaryVoiceBan(voiceChannel: VoiceChannel, member: Member, logChannel: MessageChannel): Unit = {
    val originalPerms = voiceChannel.getPermissionAttachment(member)

    if (!originalPerms.denies.contains(Permission.VOICE_CONNECT)) {
      val permsWithVoiceBan = originalPerms.deny(Permission.VOICE_CONNECT)
      val futureReq = APIHelper.tryRequest({
        // XXX Oh my god static mutable globals in a multithreaded environment
        // XXX Hack: JDA seems to consistently get the wrong idea about permissions here for some reason.
        Manager.setPermissionChecksEnabled(false)
        try {
          voiceChannel.applyPerms(PermissionCollection(Seq(member -> permsWithVoiceBan)))
        } finally
          Manager.setPermissionChecksEnabled(true)
      }, onFail = APIHelper.loudFailure(s"adding voice tempban permissions to ${voiceChannel.mention} for ${member.getUser.mention}", logChannel))

      // Whether we need to explicitly grant the permission back
      val explicitGrant = originalPerms.allows.contains(Permission.VOICE_CONNECT)

      futureReq.foreach { _ =>
        // Take note (in DB) of when the voice ban should expire
        val expiryTimestamp = System.currentTimeMillis() + (10 minutes).toMillis
        voiceBanExpiries(voiceBanKey(voiceChannel, member)) = VoiceBanExpiry(expiryTimestamp, explicitGrant)

        // Expire voice ban using scheduler
        scheduler.schedule(10 minutes) {
          removeTemporaryVoiceBan(voiceChannel, member, Some(logChannel), explicitGrant)
        }
      }
    }
  }

  def kickVoiceMember(voiceChannel: VoiceChannel, member: Member, logChannel: MessageChannel): Unit = {
    for {
      voiceState <- Option(member.getVoiceState)
      if voiceState.getChannel == voiceChannel
    } APIHelper.tryRequest(voiceChannel.getGuild.kickVoiceMember(member),
      onFail = APIHelper.loudFailure(s"kicking ${member.getUser.mention} from voice chat", logChannel))
  }

  private def completeKick(channel: TextChannel, kickState: KickState, myMessage: ID[Message]): Unit = {
    val maybeResults = for {
      member <- kickState.target.find(channel.getGuild)
        .toRight("Could not find the member to kick them from the channel")
      voiceChannel <- channel.getGuild.findVoiceChannel(kickState.channel)
        .toRight("Could not find the voice channel to kick from")
    } yield (member, voiceChannel)

    maybeResults.left.foreach { err => channel ! BotMessages.error(err) }
    maybeResults.foreach { case (member, voiceChannel) =>
      addTemporaryVoiceBan(voiceChannel, member, channel)
      kickVoiceMember(voiceChannel, member, channel)
      updateVoteKickMessage(channel, kickState, myMessage)
      channel ! BotMessages.plain(s"The vote has passed and ${member.getUser.mention} will be kicked from ${voiceChannel.mention}")
    }
  }

  def updateVoteKickMessage(channel: TextChannel, kickState: KickState, myMessage: ID[Message]): Unit = {
    APIHelper.tryRequest(channel.editMessageById(myMessage.value, makeMessageContents(kickState, channel.getGuild)),
      onFail = APIHelper.failure("editing voice kick results"))
    if (kickState.ended) {
      APIHelper.tryRequest(channel.clearReactionsById(myMessage.value))
      removePendingKick(channel, myMessage)
    }
  }

  private def updateKickVote(channel: TextChannel, myMessage: ID[Message], voteType: VoteType, member: Member): Unit = {
    Future {
      blocking {
        pendingKicks.synchronized {
          pendingKicks.get(myMessage)
            // ensure the vote comes from an eligible voter
            .filter { kickState => kickState.votes.contains(member.id) && !kickState.expired }
            .map { kickState =>
              val newVotes = kickState.votes + (member.id -> Some(voteType))
              val newKickState = kickState.copy(votes = newVotes)

              val result = newKickState.overallVote

              if (result.isEmpty) pendingKicks(myMessage) = newKickState
              else removePendingKick(channel, myMessage)

              newKickState
            }
        }
      }.foreach {
        afterUpdateKickState(channel, myMessage, _)
      }
    }
  }

  private def removePendingKick(channel: TextChannel, myMessage: ID[Message]) =
    pendingKicks.synchronized {
      pendingKicks.remove(myMessage).tap { kickStateMaybe =>
        for (kickState <- kickStateMaybe; member <- kickState.votes.keys) {
          kickMessagesByMember(member) -= ((channel.id, myMessage))
        }
      }
    }

  private def afterUpdateKickState(channel: TextChannel, myMessage: ID[Message], kickState: KickState): Unit = {
    kickState.overallVote match {
      case Some(KickVote) => completeKick(channel, kickState, myMessage)
      case _ => updateVoteKickMessage(channel, kickState, myMessage)
    }
  }

  private def removeUserFromVote(member: Member): Unit = Future {
    implicit val jda: JDA = member.getJDA
    blocking {
      pendingKicks.synchronized {
        val oldMessages = kickMessagesByMember(member.id)

        // Remove user from all the votes they are taking part in
        for ((_, message) <- oldMessages) {
          val kickState = pendingKicks(message)
          pendingKicks(message) = kickState.copy(votes = kickState.votes - member.id)
        }
        kickMessagesByMember(member.id) = Set.empty

        // Take note of kick votes that need updating now that a user has dropped out
        oldMessages.view.map(tup => (tup, pendingKicks(tup._2))).toSeq
      }
    }.foreach { case ((textChannelId, messageId), kickState) =>
      textChannelId.find.foreach { textChannel =>
        afterUpdateKickState(textChannel, messageId, kickState)
      }
    }
  }

  override def onEvent(event: GenericEvent): Unit = event match {
    case _: ReadyEvent =>
      implicit val jda: JDA = event.getJDA
      for {
        expiries <- voiceBanExpiries.items
        ((guildId, channelId, userId), VoiceBanExpiry(expiryTime, explicitGrant)) <- expiries
      } {
        val scheduledUnban = for {
          guild <- guildId.find
          channel <- channelId.find
          user <- userId.find
          member <- guild.findMember(user)
        } yield scheduler.schedule((0L max (expiryTime - System.currentTimeMillis())) milliseconds) {
          removeTemporaryVoiceBan(channel, member, None, explicitGrant)
        }

        if (scheduledUnban.isEmpty) {
          // One of the entities (e.g. voice channel) no longer exists, so not applicable
          voiceBanExpiries.remove((guildId, channelId, userId))
        }
      }
    case NonBotReact(React.Text(emoji), msgId, channel: TextChannel, user) =>
      for {
        vote <- getEmojiMeaning(emoji)
        member <- Option(channel.getGuild.getMember(user))
      } updateKickVote(channel, msgId, vote, member)
    case GuildVoiceUpdate(member, Some(_), _) =>
      removeUserFromVote(member)
    case _ =>
  }
}

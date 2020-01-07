package score.discord.generalbot.functionality

import net.dv8tion.jda.api.entities._
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.{JDA, Permission}
import score.discord.generalbot.collections.ReplyCache
import score.discord.generalbot.command.Command
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.IdConversions._
import score.discord.generalbot.wrappers.jda.matching.Events.NonBotReact
import score.discord.generalbot.wrappers.jda.matching.React

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, blocking}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class VoiceKick(implicit messageOwnership: MessageOwnership, replyCache: ReplyCache, scheduler: Scheduler) extends EventListener {

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

    lazy val passed: Boolean = sumVotes {
      case StayVote => 0
      case AbstainVote => 1
      case KickVote => 2
    } > votes.size

    lazy val failed: Boolean = sumVotes {
      case StayVote => 2
      case AbstainVote => 1
      case KickVote => 0
    } >= votes.size

    def overallVote: Option[VoteType] =
      if (passed) Some(KickVote)
      else if (failed) Some(StayVote)
      else None

    // TODO: There is no actual expiry mechanic other than cosmetically
    def expired: Boolean = System.currentTimeMillis() >= expiry
  }

  private val pendingKicks = mutable.Map.empty[ID[Message], KickState]

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

          guildTextChannel <- ensureIsGuildChannel(textChannel)

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
          (kickState, msg)
        }

        result.left.foreach { err => message reply BotMessages.error(err) }

        for (resultRight <- result;
             (kickState, successMsg) = resultRight;
             botMsg <- message reply successMsg) {
          // Record our message ID and initial kick state in pendingKicks
          blocking {
            pendingKicks.synchronized {
              pendingKicks += botMsg.id -> kickState
            }
          }
          botMsg.addReaction(KickVote.emoji).queue()
          botMsg.addReaction(AbstainVote.emoji).queue()
          botMsg.addReaction(StayVote.emoji).queue()
        }
      }

      private def ensureIsGuildChannel(textChannel: MessageChannel): Either[String, GuildChannel] =
        textChannel match {
          case c: GuildChannel => Right(c)
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

    s"A vote to kick $targetMention from $chanMention has been called.\n" +
      s"$usersWhoShouldVote, please vote for (${KickVote.emoji}) " +
      s"or against (${StayVote.emoji}) the kick, " +
      s"or abstain (${AbstainVote.emoji}) to exclude yourself from the vote.\n\n" +
      s"**Votes**: $votesSoFar\n$finalResult"
  }

  private def getEmojiMeaning(emoji: String): Option[VoteType] = emoji match {
    case KickVote.emoji => Some(KickVote)
    case AbstainVote.emoji => Some(AbstainVote)
    case StayVote.emoji => Some(StayVote)
    case _ => None
  }

  def removeTemporaryVoiceBan(voiceChannel: VoiceChannel, member: Member, logChannel: MessageChannel, explicitGrant: Boolean): Unit = {
    Option(voiceChannel.getPermissionOverride(member)).foreach { permissionOverride =>
      val originalPerms = (permissionOverride.getAllowedRaw, permissionOverride.getDeniedRaw)
      val permsWithoutVoiceBan =
        (originalPerms._1, originalPerms._2 & ~Permission.VOICE_CONNECT.getRawValue)

      APIHelper.tryRequest({
        if ((permsWithoutVoiceBan._1 | permsWithoutVoiceBan._2) == 0) {
          // if all permissions are inherited after removing voice ban,
          // then remove the override
          permissionOverride.delete()
        } else if (explicitGrant) {
          permissionOverride.getManager.grant(Permission.VOICE_CONNECT)
        } else {
          permissionOverride.getManager.clear(Permission.VOICE_CONNECT)
        }
      }, onFail = APIHelper.loudFailure(
        s"undoing voice tempban permissions in ${voiceChannel.mention} for ${member.getUser.mention}", logChannel))
    }
  }

  def addTemporaryVoiceBan(voiceChannel: VoiceChannel, member: Member, logChannel: MessageChannel): Unit = {
    val originalPerms =
      Option(voiceChannel.getPermissionOverride(member))
        .map(p => (p.getAllowedRaw, p.getDeniedRaw))
        .getOrElse((0L, 0L))

    if ((originalPerms._2 & Permission.VOICE_CONNECT.getRawValue) == 0) {
      val futureReq = APIHelper.tryRequest(
        voiceChannel.getManager.putPermissionOverride(member,
          originalPerms._1 & ~Permission.VOICE_CONNECT.getRawValue,
          originalPerms._2 | Permission.VOICE_CONNECT.getRawValue),
        onFail = APIHelper.loudFailure(s"adding voice tempban permissions to ${voiceChannel.mention} for ${member.getUser.mention}", logChannel))
      // TODO: Make more reliable by putting into DB
      val explicitGrant = (originalPerms._1 & Permission.VOICE_CONNECT.getRawValue) != 0
      futureReq.foreach { _ =>
        scheduler.schedule(10 minutes) {
          removeTemporaryVoiceBan(voiceChannel, member, logChannel, explicitGrant)
        }
      }
    }
  }

  def kickVoiceMember(voiceChannel: VoiceChannel, member: Member, logChannel: MessageChannel): Unit = {
    for {
      voiceState <- Option(member.getVoiceState)
      if voiceState.getChannel == voiceChannel
    } APIHelper.tryRequest(voiceChannel.getGuild.kickVoiceMember(member),
      onFail = APIHelper.loudFailure(s"kicking a ${member.getUser.mention} from voice chat", logChannel))
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
  }

  private def updateKickVote(channel: TextChannel, myMessage: ID[Message], voteType: VoteType, member: Member): Unit = {
    Future {
      blocking {
        pendingKicks.synchronized {
          pendingKicks.get(myMessage)
            // ensure the vote comes from an eligible voter
            .filter { kickState => kickState.votes.contains(member.id) }
            .map { kickState =>
              val newVotes = kickState.votes + (member.id -> Some(voteType))
              val newKickState = kickState.copy(votes = newVotes)

              val result = newKickState.overallVote

              if (result.isEmpty) pendingKicks(myMessage) = newKickState
              else pendingKicks.remove(myMessage)

              newKickState
            }
        }
      }.foreach { kickState =>
        kickState.overallVote match {
          case Some(KickVote) => completeKick(channel, kickState, myMessage)
          case _ => updateVoteKickMessage(channel, kickState, myMessage)
        }
      }
    }
  }

  override def onEvent(event: GenericEvent): Unit = event match {
    case NonBotReact(React.Text(emoji), msgId, channel: TextChannel, user) =>
      for {
        vote <- getEmojiMeaning(emoji)
        member <- Option(channel.getGuild.getMember(user))
      } updateKickVote(channel, msgId, vote, member)
    case _ =>
  }
}

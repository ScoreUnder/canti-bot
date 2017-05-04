package score.discord.generalbot.functionality

import java.util
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities._
import net.dv8tion.jda.core.events.guild.voice.{GuildVoiceLeaveEvent, GuildVoiceMoveEvent}
import net.dv8tion.jda.core.events.{Event, ReadyEvent}
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.hooks.EventListener
import net.dv8tion.jda.core.requests.restaction.ChannelAction
import score.discord.generalbot.command.Command
import score.discord.generalbot.util._
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._
import slick.jdbc.SQLiteProfile.api._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

class PrivateVoiceChats(database: Database, commands: Commands)(implicit scheduler: Scheduler) extends EventListener {
  private val invites = new ConcurrentHashMap[GuildUserId, Invite]()
  private val userByChannel = new UserByChannel(database, "user_created_channels")

  private type Timestamp = Long

  case class Invite(from: Long, channel: Long, expiry: Timestamp) {
    def valid = System.currentTimeMillis() < expiry
  }

  {
    val accept = new Command.Anyone {
      override def name = "accept"

      override def aliases = List("acc")

      override def description = "Accepts another user's invitation to join a voice channel."

      override def execute(message: Message, args: String): Unit = {
        val channel = message.getChannel

        message.delete.queue()

        def ensureInviteValid(inv: Invite) =
          if (inv.valid) Right(inv)
          else Left("Your last invite expired. Please ask for another.")

        val member = CommandHelper(message).member
        val result = for {
          member <- member
          inv <- Option(invites get GuildUserId(member))
            .toRight("You don't have any pending voice chat invitations.")
          _ <- ensureInviteValid(inv)
          voiceChannel <- Option(message.getGuild.getVoiceChannelById(inv.channel))
            .toRight("The voice channel you were invited to no longer exists.")
          memberName = member.getEffectiveName
          voiceMention = s"<#${voiceChannel.id}>"
          _ <- Try(message.getGuild.getController.moveVoiceMember(member, voiceChannel).queue(
            { _ =>
              invites.remove(GuildUserId(member))
              channel sendTemporary BotMessages
                .okay(s"Moved you into the $voiceMention channel.")
                .setTitle(s"$memberName: Success!", null)
            },
            APIHelper.loudFailure("moving you into another channel", channel)
          )).toEither.left.map(translateChannelMoveError)
        } yield ()

        for (err <- result.left) {
          val errEmbed = BotMessages.error(err)
          for (member <- member)
            errEmbed.setTitle(s"${member.getEffectiveName}: Error", null)
          channel sendTemporary errEmbed
        }
      }
    }

    val invite = new Command.Anyone {
      override def name = "invite"

      override val aliases = List("inv")

      override def description = "Asks another user to join your current voice channel"

      override def execute(message: Message, args: String): Unit = {
        (for {
          member <- CommandHelper(message).member
          chan <- Option(member.getVoiceState.getChannel)
            .toRight("You must be in voice chat to use this command.")
          success <- message.getMentionedUsers.asScala match {
            case Seq() => Left("You must mention the users you want to join you in voice chat.")
            case Seq(mentions@_*) =>
              for (mention <- mentions)
                invites.put(
                  GuildUserId(chan.getGuild.id, mention.id),
                  Invite(message.getAuthor.id, chan.id, System.currentTimeMillis() + (10 minutes).toMillis)
                )

              val mentioned = mentions map {
                case you if you == message.getAuthor => ">(You)"
                case user => user.mention
              }
              Right(
                s"""${mentioned mkString ", "}:
                   |You have been invited to join ${message.getAuthor.mention} in voice chat.
                   |Please join a voice channel and type `${commands.prefix}${accept.name}`
                   |to accept this invitation.""".stripMargin
              )
          }
        } yield success) match {
          case Left(err) => message.getChannel ! BotMessages.error(err)
          case Right(succ) => message.getChannel ! succ
        }
      }
    }

    commands register accept
    commands register invite
    commands register new Command.ServerAdminDiscretion {
      override def name = "private"

      override val aliases = List("prv", "privatevoice", "privatechat", "prvoice")

      override def description = "Creates a private voice chat channel"

      override val longDescription =
        s"""This command creates a semi-private voice channel.
           |You can set a user limit (e.g. `${commands.prefix}$name 4`), or leave it blank to make it completely private.
           |You may also invite other users there using the `${commands.prefix}${invite.name}` command.
           |The name of the channel can be set by adding it to the end of the command.
           |e.g. `${commands.prefix}$name 3 Hangout number 1`""".stripMargin

      override def execute(message: Message, args: String): Unit = {
        val result =
          for {
            member <- CommandHelper(message).member
            voiceChannel <- Option(member.getVoiceState.getChannel)
              .toRight("You need to join voice chat before you can do this.")
            (limit, name) = parseChannelDetails(args, voiceChannel)
            guild = message.getGuild
            channelReq <- createChannel(name, guild)
          } yield {
            val channel = message.getChannel

            addChannelPermissions(channelReq, member, limit)
            channelReq.queue(
              { voiceChannel =>
                userByChannel.synchronized {
                  userByChannel(voiceChannel) = message.getAuthor
                }

                // TODO: Fix your shit JDA (asInstanceOf cast)
                Try(guild.getController.moveVoiceMember(member, voiceChannel.asInstanceOf[VoiceChannel]).queue(
                  _ => (), APIHelper.loudFailure("moving you into the newly created channel", channel)
                )).failed.map(translateChannelMoveError).foreach { err =>
                  channel sendTemporary BotMessages.error(err)
                }

                channel sendTemporary BotMessages.okay("Your channel has been created.").setTitle("Success", null)
              }, APIHelper.loudFailure("creating a channel", channel)
            )
          }

        for (err <- result.left)
          message.getChannel ! BotMessages.error(err)
      }

      private def addChannelPermissions(channelReq: ChannelAction, member: Member, limit: Int) = {
        val guild = member.getGuild
        if (limit == 0)
        // If no limit, deny access to all users by default
          channelReq
            .addPermissionOverride(
              guild.getPublicRole,
              Collections.emptyList[Permission], util.Arrays.asList(Permission.VOICE_CONNECT)
            )
        else
        // Otherwise, if there is a limit, use that and don't add extra permissions
          channelReq
            .setUserlimit(limit)

        channelReq
          .addPermissionOverride(
            guild.getSelfMember,
            SELF_PRIVATE_CHANNEL_PERMISSIONS, Collections.emptyList[Permission]
          )
          .addPermissionOverride(
            member, CREATOR_PRIVATE_CHANNEL_PERMISSIONS, Collections.emptyList[Permission]
          )
      }

      private def parseChannelDetails(args: String, originalChannel: VoiceChannel) = {
        val trimmedArgs = args.trim
        val (limit, name) = trimmedArgs.split(" ", 2) match {
          case Array(limitStr, name_) => (limitStr, name_.trim)
          case Array(maybeLimit) => (maybeLimit, "")
        }

        Try(limit.toInt).toOption
          .filter(x => x >= 0 && x <= 99)
          .map((_, name))
          .getOrElse((0, trimmedArgs))
        match {
          case (limit_, name_) if name_.length > 100 => (limit_, name_ take 100)
          case (limit_, name_) if name_.length < 3 => (limit_, s"Private ${originalChannel.name}")
          case x => x
        }
      }

      private def createChannel(name: String, guild: Guild) =
        Try(guild.getController.createVoiceChannel(name)).toEither.left.map({
          case _: PermissionException =>
            "I don't have permission to create a voice channel. A server administrator will need to fix this."
          case x =>
            System.err.println("Printing a stack trace for failed channel creation:")
            x.printStackTrace()
            "Unknown error occurred when trying to create your channel."
        })

      override def getIdLong = 253013831501437455L
    }
  }

  private val CREATOR_PRIVATE_CHANNEL_PERMISSIONS =
    util.Arrays.asList(Permission.MANAGE_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS)

  private val SELF_PRIVATE_CHANNEL_PERMISSIONS =
    util.Arrays.asList(Permission.MANAGE_CHANNEL, Permission.VOICE_CONNECT)

  private def translateChannelMoveError(ex: Throwable) = ex match {
    case _: IllegalStateException =>
      "You need to join voice chat before I can move you into a channel."
    case _: PermissionException =>
      "I don't have permission to move you to another voice channel. A server administrator will need to fix this."
    case _ =>
      APIHelper.failure("moving a user to a newly created channel")(ex)
      "An error occurred while trying to move you to another channel."
  }

  override def onEvent(event: Event): Unit = event match {
    case ev: ReadyEvent =>
      val jda = ev.getJDA
      scheduler submit {
        userByChannel.synchronized {
          val toRemove = new mutable.HashSet[(Long, Long)]

          for (((guildId, channelId), _) <- userByChannel) {
            Option(jda.getGuildById(guildId)) flatMap { guild =>
              Option(guild.getVoiceChannelById(channelId))
            } match {
              case None =>
                toRemove += ((guildId, channelId))
              case Some(channel) =>
                if (channel.getMembers.isEmpty) {
                  Try(channel.delete.queue(
                    _ => userByChannel.synchronized {
                      userByChannel remove channel
                    },
                    APIHelper.failure("deleting unused private channel")
                  )).failed.foreach(APIHelper.failure("deleting unused private channel"))
                }
            }
          }

          for ((guildId, channelId) <- toRemove)
            userByChannel.remove(guildId, channelId)
        }
      }

    case _: GuildVoiceLeaveEvent | _: GuildVoiceMoveEvent =>
      // TODO: Fix your shit JDA
      val channel = event match {
        case ev: GuildVoiceLeaveEvent => ev.getChannelLeft
        case ev: GuildVoiceMoveEvent => ev.getChannelLeft
      }
      scheduler submit {
        if (channel.getMembers.isEmpty && userByChannel.synchronized(userByChannel(channel)).isDefined) {
          Try(channel.delete.queue(
            _ => userByChannel.synchronized {
              userByChannel remove channel
            },
            APIHelper.failure("deleting unused private channel")
          )).failed.foreach(APIHelper.failure("deleting unused private channel"))
        }
      }

    case _ =>
  }
}

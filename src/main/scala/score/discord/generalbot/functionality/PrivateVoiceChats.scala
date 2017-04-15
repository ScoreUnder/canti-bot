package score.discord.generalbot.functionality

import java.util.concurrent.ConcurrentHashMap

import net.dv8tion.jda.core.entities.{Message, TextChannel}
import net.dv8tion.jda.core.events.{Event, ReadyEvent}
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.command.Command
import score.discord.generalbot.util.{BotMessages, GuildUserId}
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class PrivateVoiceChats(commands: Commands)(implicit scheduler: Scheduler) extends EventListener {
  private val invites = new ConcurrentHashMap[GuildUserId, Invite]()

  private type Timestamp = Long

  case class Invite(from: Long, channel: Long, expiry: Timestamp) {
    def valid = System.currentTimeMillis() < expiry
  }

  {
    def getMember(message: Message) =
      (message.getChannel match {
        case chan: TextChannel => Right(chan)
        case _ => Left("You can only use this command from within a server.")
      }) flatMap { (chan) =>
        Option(chan.getGuild.getMember(message.getAuthor)) match {
          case Some(member) => Right(member)
          case None => Left("Internal error: Can't find your server membership. This might be a temporary problem.")
        }
      }

    val accept = new Command.Anyone {
      override def name = "accept"

      override def aliases = List("acc")

      override def description = "Accepts another user's invitation to join a voice channel."

      override def execute(message: Message, args: String): Unit = {
        val member = getMember(message)
        val channel = message.getChannel

        message.delete.queue()

        member flatMap { member =>
          Option(invites get GuildUserId(member))
            .toRight("You don't have any pending voice chat invitations.")
        } flatMap {
          case inv if !inv.valid => Left("Your last invite expired. Please ask for another.")
          case inv =>
            Option(message.getGuild.getVoiceChannelById(inv.channel))
              .toRight("The voice channel you were invited to no longer exists.")
        } match {
          case Right(voiceChannel) =>
            val memberMention = member.right.get.getAsMention
            val voiceMention = s"<#${voiceChannel.getIdLong}>"

            message.getGuild.getController.moveVoiceMember(member.right.get, voiceChannel).queue(
              { _ =>
                invites.remove(GuildUserId(member.right.get))
                channel sendTemporary BotMessages
                  .okay(s"Moved you into the $voiceMention channel.")
                  .setTitle(s"$memberMention: Success!", null)
              }, { error =>
                val msg = error match {
                  case _: PermissionException =>
                    "I don't have permission to do that. A server administrator will need to fix this."
                  case _: IllegalStateException =>
                    "You need to join voice chat before you can accept the invite."
                  case x =>
                    System.err.println("Printing a stack trace for failed invite:")
                    x.printStackTrace()
                    "Unknown error occurred when trying to accept your invite."
                }
                channel sendTemporary BotMessages.error(msg).setTitle(s"$memberMention: Error", null)
              }
            )
          case Left(error) =>
            channel sendTemporary BotMessages.error(error).setTitle(s"${member.right.get.getAsMention}: Error", null)
        }
      }
    }

    val invite = new Command.Anyone {
      override def name = "invite"

      override val aliases = List("inv")

      override def description = "Asks another user to join your current voice channel"

      override def execute(message: Message, args: String): Unit = {
        getMember(message) flatMap { member =>
          Option(member.getVoiceState.getChannel) match {
            case Some(chan) => Right(chan)
            case None => Left("You must be in voice chat to use this command.")
          }
        } flatMap { chan =>
          message.getMentionedUsers.asScala match {
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
                   | You have been invited to join ${message.getAuthor.mention} in voice chat.
                   | Please join a voice channel and type `${commands.prefix}${accept.name}`
                   | to accept this invitation.""".stripMargin
              )
          }
        } match {
          case Left(err) => message.getChannel ! BotMessages.error(err)
          case Right(succ) => message.getChannel ! succ
        }
      }
    }

    commands register new Command.Anyone {
      override def name = "private"

      override val aliases = List("prv", "privatevoice", "privatechat", "prvoice")

      override def description = "Creates a private voice chat channel"

      override val longDescription =
        s"This command creates a semi-private voice channel. " +
          s"You can set a user limit (e.g. `${commands.prefix}$name 4`), or leave it blank to make it completely private. " +
          s"You may also invite other users there using the `${commands.prefix}${invite.name}` command."

      override def execute(message: Message, args: String): Unit = {

      }
    }
  }

  override def onEvent(event: Event): Unit = event match {
    case ev: ReadyEvent =>
    // TODO: Setup code

    case ev =>
  }
}

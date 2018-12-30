package score.discord.generalbot.functionality

import net.dv8tion.jda.core.entities.{Message, Role}
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.collections.{CommandPermissionLookup, MessageCache, ReplyCache}
import score.discord.generalbot.command.Command
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.FutureOption._
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.matching.Events.{NonBotMessage, NonBotMessageEdit}
import score.discord.generalbot.wrappers.Tap._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class Commands(val permissionLookup: CommandPermissionLookup)(implicit exec: Scheduler, messageCache: MessageCache, replyCache: ReplyCache) extends EventListener {
  // All commands and aliases, indexed by name
  private val commands = mutable.HashMap[String, Command]()
  // Commands list excluding aliases
  private val commandList = mutable.TreeSet[Command]()(_.name compare _.name)
  // String prepended before a command
  val prefix = "&"

  def register(command: Command): Unit = {
    commands(command.name) = command
    for (alias <- command.aliases) {
      commands(alias) = command
    }
    commandList += command
  }

  def get(commandName: String) = commands.get(commandName)

  def all = commandList.toList

  def requiredRole(cmd: Command, message: Message): Future[Option[Role]] =
    cmd match {
      case cmd: Command.ServerAdminDiscretion =>
        for {
          member <- Future.successful(Option(message.getMember)).flatView
          role <- permissionLookup(cmd, member.getGuild).flatView
        } yield role
      case _ => Future.successful(None)
    }

  def isAllowedOnServer(cmd: Command, message: Message): Future[Boolean] =
    (for {
      role <- requiredRole(cmd, message).flatView
      member <- Future.successful(Option(message.getMember)).flatView
    } yield member has role).map(_ getOrElse true)

  def canRunCommand(cmd: Command, message: Message): Future[Either[String, Command]] =
    if (!(cmd checkPermission message))
      Future.successful(Left(cmd.permissionMessage))
    else {
      isAllowedOnServer(cmd, message).map {
        case true => Right(cmd)
        case false => Left("The usage of that command is restricted on this server.")
      }
    }

  def splitCommand(messageRaw: String, requirePrefix: Boolean = true): Option[(String, String)] = {
    val hasPrefix = messageRaw.startsWith(prefix)
    if (requirePrefix && !hasPrefix)
      None
    else {
      val unprefixed = if (hasPrefix) messageRaw.drop(prefix.length) else messageRaw
      val split = unprefixed.split("[\\s　]", 2)

      val cmdName = split(0)
      val cmdExtra = if (split.length < 2) "" else split(1)

      Some((cmdName, cmdExtra))
    }
  }

  def parseCommand(input: String): Option[(Command, String)] = for {
    (cmdName, cmdExtra) <- splitCommand(input)
    cmd <- commands.get(cmdName)
  } yield (cmd, cmdExtra)

  def runIfAllowed(message: Message, cmd: Command, cmdExtra: String): Future[Either[String, Command]] =
    canRunCommand(cmd, message).tap(_ onComplete {
      case Success(Right(_)) => cmd.execute(message, cmdExtra)
      case Success(Left(err)) => message.getChannel sendTemporary BotMessages.error(err)
      case Failure(ex) => ex.printStackTrace()
    })

  override def onEvent(event: Event) {
    event match {
      case NonBotMessage(message) =>
        for ((cmd, cmdExtra) <- parseCommand(message.getContentRaw)) {
          runIfAllowed(message, cmd, cmdExtra)
        }
      case NonBotMessageEdit(oldMsg, newMsg) =>
        for ((cmd, cmdExtra) <- parseCommand(newMsg.getContentRaw)) {
          parseCommand(oldMsg.text) match {
            case None =>
              runIfAllowed(newMsg, cmd, cmdExtra)
            case Some((`cmd`, _)) =>
              canRunCommand(cmd, newMsg) onComplete {
                case Success(Right(_)) => cmd.executeForEdit(newMsg, replyCache.get(oldMsg.messageId), cmdExtra)
                case Success(Left(err)) => // Do not print error for edits to command with no perms
                case Failure(ex) => ex.printStackTrace()
              }
            case Some((_, _)) =>
              runIfAllowed(newMsg, cmd, cmdExtra) foreach {
                case Right(_) => replyCache.get(oldMsg.messageId).foreach { replyId =>
                  APIHelper.tryRequest(newMsg.getChannel.deleteMessageById(replyId.value),
                    onFail = APIHelper.failure("deleting old command reply"))
                }
                case _ =>
              }
          }
        }
      case _ =>
    }
  }
}

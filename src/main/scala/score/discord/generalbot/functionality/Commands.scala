package score.discord.generalbot.functionality

import net.dv8tion.jda.api.entities.{Message, Role}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
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

  /** Normalises a command name string into a form suitable to be looked up
    * as a key in the command map.
    * Not guaranteed to behave similarly between versions.
    *
    * @param name command name
    * @return name in normalised form
    */
  private def normaliseCommandName(name: String): String = name.toLowerCase

  /** Register a command with this command registry. The command may then be
    * retrieved via its main name or any of its aliases, and will be available
    * to the command-dispatching event listener.
    *
    * @param command command to register
    */
  def register(command: Command): Unit = {
    commands(normaliseCommandName(command.name)) = command
    for (alias <- command.aliases) {
      commands(normaliseCommandName(alias)) = command
    }
    commandList += command
  }

  /** Retrieve a command by name from this command registry.
    *
    * @param commandName name or alias of command
    * @return optional command
    */
  def get(commandName: String): Option[Command] =
    commands.get(normaliseCommandName(commandName))

  /** Retrieve all registered commands from this command registry.
    *
    * @return collection of all commands
    */
  def all: Seq[Command] = commandList.toList

  /** Look up the role required to execute this command. Results in a future
    * None if anyone can use the command, or a future Some if it is restricted
    * to a specific role.
    *
    * @param cmd command to look up
    * @param message command message (to determine guild)
    * @return required role
    */
  def requiredRole(cmd: Command, message: Message): Future[Option[Role]] =
    cmd match {
      case cmd: Command.ServerAdminDiscretion =>
        for {
          member <- Future.successful(Option(message.getMember)).flatView
          role <- permissionLookup(cmd, member.getGuild).flatView
        } yield role
      case _ => Future.successful(None)
    }

  /** Determines whether a command corresponding to a given message can be
    * executed on that guild by that member, skipping other checks like
    * server admin status.
    *
    * @param cmd command to check
    * @param message command message
    * @return whether allowed or not
    */
  def isAllowedOnServer(cmd: Command, message: Message): Future[Boolean] =
    (for {
      role <- requiredRole(cmd, message).flatView
      member <- Future.successful(Option(message.getMember)).flatView
    } yield member has role).map(_ getOrElse true)

  /** Determines whether a command corresponding to a given message can be
    * executed on that guild by that member, and if not returns a human-readable
    * error message.
    *
    * @param cmd command to check
    * @param message command message
    * @return either error or command
    */
  def canRunCommand(cmd: Command, message: Message): Future[Either[String, Command]] =
    if (!(cmd checkPermission message))
      Future.successful(Left(cmd.permissionMessage))
    else {
      isAllowedOnServer(cmd, message).map {
        case true => Right(cmd)
        case false => Left("The usage of that command is restricted on this server.")
      }
    }

  /** Splits a raw message into command name and arguments. No validation is
    * done to check that the name is correct in any way.
    *
    * @param messageRaw message to parse command from
    * @param requirePrefix whether command prefix is necessary
    * @return optionally (name, args) of command
    */
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

  /** Parses a command string into the command object and argument string.
    *
    * @param input command string
    * @return optionally (command, args)
    */
  def parseCommand(input: String): Option[(Command, String)] = for {
    (cmdName, cmdExtra) <- splitCommand(input)
    cmd <- get(cmdName)
  } yield (cmd, cmdExtra)

  def runIfAllowed(message: Message, cmd: Command, cmdExtra: String): Future[Either[String, Command]] =
    canRunCommand(cmd, message).tap(_ onComplete {
      case Success(Right(_)) => cmd.execute(message, cmdExtra)
      case Success(Left(err)) => message.getChannel sendTemporary BotMessages.error(err)
      case Failure(ex) => ex.printStackTrace()
    })

  override def onEvent(event: GenericEvent) {
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
                case Success(Left(_)) => // Do not print error for edits to command with no perms
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

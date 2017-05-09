package score.discord.generalbot.functionality

import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.command.Command
import score.discord.generalbot.util.{BotMessages, CommandPermissionLookup}
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.collection.mutable

class Commands(val permissionLookup: CommandPermissionLookup)(implicit exec: Scheduler) extends EventListener {
  // All commands and aliases, indexed by name
  private val commands = mutable.HashMap[String, Command]()
  // Commands list excluding aliases
  private val commandList = {
    implicit val commandOrdering: Ordering[Command] =
      (x, y) => x.name compare y.name
    mutable.TreeSet[Command]()
  }
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

  def requiredRole(cmd: Command, message: Message) =
    cmd match {
      case cmd: Command.ServerAdminDiscretion =>
        for {
          member <- Option(message.getMember)
          role <- permissionLookup(cmd, member.getGuild)
        } yield role
      case _ => None
    }

  def isAllowedOnServer(cmd: Command, message: Message) =
    (for {
      role <- requiredRole(cmd, message)
      member <- Option(message.getMember)
    } yield member has role) getOrElse true

  def canRunCommand(cmd: Command, message: Message) =
    if (!(cmd checkPermission message))
      Left(cmd.permissionMessage)
    else if (!isAllowedOnServer(cmd, message))
      Left("The usage of that command is restricted on this server.")
    else
      Right(cmd)

  def splitCommand(message: Message, requirePrefix: Boolean = true) = {
    val messageRaw = message.getRawContent
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

  override def onEvent(event: Event) {
    event match {
      case ev: MessageReceivedEvent =>
        if (ev.getAuthor.isBot) return

        val message = ev.getMessage
        for {
          (cmdName, cmdExtra) <- splitCommand(message)
          cmd <- commands.get(cmdName)
        } {
          canRunCommand(cmd, message) match {
            case Right(_) => cmd.execute(message, cmdExtra)
            case Left(err) => ev.getChannel sendTemporary BotMessages.error(err)
          }
        }
      case _ =>
    }
  }
}

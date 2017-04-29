package score.discord.generalbot.functionality

import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.command.Command
import score.discord.generalbot.util.BotMessages
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.collection.mutable

class Commands extends EventListener {
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

  // def apply(commandName: String) = commands(commandName)

  def get(commandName: String) = commands.get(commandName)

  def all = commandList.toList

  def length = commandList.size

  override def onEvent(event: Event) {
    event match {
      case ev: MessageReceivedEvent =>
        if (ev.getAuthor.isBot) return

        val message = ev.getMessage.getRawContent
        if (message.startsWith(prefix)) {
          val pivot = message.indexOf(' ') match {
            case -1 => message.length
            case x => x
          }

          val cmdName = message.slice(prefix.length, pivot)
          val cmdExtra = message drop pivot + 1
          for (cmd <- commands.get(cmdName)) {
            if (cmd checkPermission ev.getMessage) {
              cmd.execute(ev.getMessage, cmdExtra)
            } else {
              ev.getChannel ! BotMessages.error(cmd.permissionMessage)
            }
          }
        }

      case _ =>
    }
  }
}

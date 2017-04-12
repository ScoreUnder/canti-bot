package score.discord.generalbot.functionality

import net.dv8tion.jda.core.{EmbedBuilder, Permission}
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.GeneralBot
import score.discord.generalbot.command.Command

import scala.collection.mutable

class Commands extends EventListener {
  private val commands = mutable.HashMap[String, Command]()
  private val prefix = "&"

  def registerCommand(command: Command): Unit = {
    commands(command.name) = command
    for (alias <- command.aliases) {
      commands(alias) = command
    }
  }

  def apply(commandName: String) = commands(commandName)

  def keys = commands.keys

  def length = commands.size

  override def onEvent(event: Event) {
    event match {
      case ev: MessageReceivedEvent =>
        val message = ev.getMessage.getRawContent
        if (message.startsWith(prefix)) {
          def canExecute(cmd: Command) = !cmd.isAdminOnly || ev.getMember.hasPermission(Permission.MANAGE_SERVER)

          def callCommand(name: String, arg: String) = {
            commands.get(name) foreach { cmd =>
              if (canExecute(cmd)) {
                cmd.execute(ev.getMessage, arg)
              } else {
                ev.getChannel.sendMessage(
                  new EmbedBuilder().
                    setDescription("You don't have permission to run that command.").
                    setColor(GeneralBot.ERROR_COLOR).
                    build()
                ).queue()
              }
            }
          }

          message.indexOf(' ') match {
            case -1 =>
              val cmdName = message.substring(prefix.length)
              callCommand(cmdName, "")

            case pivot =>
              val cmdName = message.substring(prefix.length, pivot)
              val cmdExtra = message.substring(pivot + 1)
              callCommand(cmdName, cmdExtra)
          }
        }

      case _ =>
    }
  }

}

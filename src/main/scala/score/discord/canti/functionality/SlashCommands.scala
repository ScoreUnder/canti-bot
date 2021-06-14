package score.discord.canti.functionality

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import org.slf4j.LoggerFactory
import score.discord.canti.command.slash.SlashCommand
import score.discord.canti.wrappers.jda.Conversions.toRichRestAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SlashCommands(commands: SlashCommand*) extends EventListener {
  private val logger = LoggerFactory.getLogger(classOf[SlashCommands])

  val commandsMap: Map[String, SlashCommand] =
    commands.map(c => normaliseCommandName(c.name) -> c).toMap

  private def normaliseCommandName(name: String): String = name.toLowerCase

  def registerCommands(what: CommandListUpdateAction): CommandListUpdateAction =
    what.addCommands(commands.map(_.data): _*)

  override def onEvent(event: GenericEvent): Unit = event match {
    case ev: ReadyEvent =>
      registerCommands(ev.getJDA.updateCommands()).queueFuture()
    case ev: SlashCommandEvent =>
      val name = normaliseCommandName(ev.getName)
      commandsMap.get(name) match {
        case None => logger.warn(s"Got unknown slash command from API: $name")
        case Some(cmd) => Future {
          cmd.execute(ev)
        }
      }
    case _ =>
  }
}
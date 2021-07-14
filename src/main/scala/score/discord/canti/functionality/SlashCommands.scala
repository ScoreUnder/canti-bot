package score.discord.canti.functionality

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import score.discord.canti.command.slash.SlashCommand
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.Conversions.{richGuild, richMessageChannel, richUser}
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SlashCommands(commands: SlashCommand*) extends EventListener:
  private val logger = loggerOf[SlashCommands]

  val commandsMap: Map[String, SlashCommand] =
    commands.map(c => normaliseCommandName(c.name) -> c).toMap

  private def normaliseCommandName(name: String): String = name.lowernn

  def registerCommands(what: CommandListUpdateAction): CommandListUpdateAction =
    what.addCommands(commands.map(_.data)*)

  override def onEvent(event: GenericEvent): Unit = event match
    case ev: ReadyEvent =>
      registerCommands(ev.getJDA.updateCommands()).queueFuture()
    case ev: SlashCommandEvent =>
      val name = normaliseCommandName(ev.getName)
      commandsMap.get(name) match
        case None => logger.warn(s"Got unknown slash command from API: $name")
        case Some(cmd) =>
          val guildStr = ev.getGuild.?.fold("no guild")(_.unambiguousString)
          logger.debug(
            s"Running slash command ${cmd.name} on behalf of user ${ev.getUser.unambiguousString} in ${ev.getChannel.unambiguousString} ($guildStr)"
          )
          Future {
            cmd.execute(ev)
          }
    case _ =>

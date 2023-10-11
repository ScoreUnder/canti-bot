package score.discord.canti.functionality

import com.codedx.util.MapK
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import score.discord.canti.collections.ReplyCache
import score.discord.canti.command.api.{ArgSpec, CommandInvocation, SlashCommandInvoker}
import score.discord.canti.command.GenericCommand
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.Conversions.{richChannel, richGuild, richMessageChannel, richUser}
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.{Commands as JCommands}

class SlashCommands(commands: GenericCommand*)(using MessageOwnership, ReplyCache)
    extends EventListener:
  private val logger = loggerOf[SlashCommands]

  val commandsMap: Map[String, GenericCommand] =
    commands.map(c => normaliseCommandName(c.name) -> c).toMap

  private def normaliseCommandName(name: String): String = name.lowernn

  def registerCommands(what: CommandListUpdateAction): CommandListUpdateAction =
    what.addCommands(commands.map { cmd =>
      JCommands.slash(cmd.name.lowernn, cmd.description).nn.addOptions(cmd.argSpec.map(_.asJda)*)
    }*).nn

  override def onEvent(event: GenericEvent): Unit = event match
    case ev: ReadyEvent =>
      registerCommands(ev.getJDA.nn.updateCommands().nn)
        .queueFuture()
        .failed
        .foreach(APIHelper.failure("registering slash commands globally"))
    case ev: SlashCommandInteractionEvent =>
      val name = normaliseCommandName(ev.getName.nn)
      commandsMap.get(name) match
        case None => logger.warn(s"Got unknown slash command from API: $name")
        case Some(cmd) =>
          val guildStr = ev.getGuild.?.fold("no guild")(_.unambiguousString)
          logger.debug(
            s"Running slash command ${cmd.name} on behalf of user " +
              s"${ev.getUser.nn.unambiguousString} in " +
              s"${ev.getChannel.?.fold("<unknown channel>")(_.unambiguousString)} ($guildStr)"
          )
          val invoker = SlashCommandInvoker(ev)
          Future {
            val args = cmd.argSpec.foldLeft(MapK.empty[ArgSpec, [T] =>> T]) { (acc, v) =>
              ev.getOption(v.asJda.getName).?.flatMap(v.argType.fromJda(invoker, _)) match
                case Some(arg) => acc + (v, arg)
                case None      => acc
            }
            Commands.logCommandInvocation(invoker, cmd)
            CommandInvocation("/", ev.getName.nn, args, invoker)
          }.foreach(Commands.runIfAllowed(_, cmd))
    case _ =>

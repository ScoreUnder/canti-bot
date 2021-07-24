package score.discord.canti.command

import net.dv8tion.jda.api.entities.{Activity, Message, User}
import score.discord.canti.command.api.{ArgSpec, ArgType, CommandInvocation, CommandPermissions}
import score.discord.canti.wrappers.jda.{ID, RetrievableMessage}
import score.discord.canti.wrappers.jda.MessageConversions.given

import scala.concurrent.Future
import scala.language.implicitConversions

class PlayCommand(owner: ID[User]) extends GenericCommand:
  override def name = "playing"

  override def description = "Set the game that this bot is currently playing"

  override val permissions = CommandPermissions.OneUserOnly(owner)

  private val playingArg =
    ArgSpec("playing", "The game to say we are playing", ArgType.GreedyString, required = false)

  override val argSpec = List(playingArg)

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    ctx.jda.getPresence.setActivity(ctx.args.get(playingArg) match
      case None       => null
      case Some(name) => Activity.playing(name)
    )
    ctx.invoker.reply("👌")

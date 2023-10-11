package score.discord.canti.command

import net.dv8tion.jda.api.entities.{Message, User}
import net.dv8tion.jda.api.entities.emoji.Emoji
import score.discord.canti.CantiBot
import score.discord.canti.command.api.{ArgSpec, CommandInvocation, CommandPermissions}
import score.discord.canti.wrappers.jda.{ID, RetrievableMessage}
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future, TimeoutException}
import scala.language.implicitConversions

class StopCommand(bot: CantiBot, owner: ID[User]) extends GenericCommand:
  override def name = "stop"

  override def aliases = List("shutdown")

  override def description = "Shut the bot down"

  override val permissions = CommandPermissions.OneUserOnly(owner)

  override def argSpec = Nil

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    val reaction =
      ctx.invoker.originatingMessage match
        case Some(message) => message.addReaction(Emoji.fromUnicode("👌")).nn.queueFuture()
        case None          => ctx.invoker.reply("👌 Shutting down...")

    // Wait a little to add the reaction, but give up quickly as shutting down is more important
    try Await.ready(reaction, 300.millis)
    catch case _: TimeoutException => ()

    bot.stop()
    Future.never

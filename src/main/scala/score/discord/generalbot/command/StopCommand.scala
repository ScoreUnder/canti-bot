package score.discord.generalbot.command

import net.dv8tion.jda.core.entities.{Message, User}
import score.discord.generalbot.GeneralBot
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

import scala.concurrent.Await
import scala.concurrent.duration._

class StopCommand(bot: GeneralBot, val userId: ID[User]) extends Command.OneUserOnly {
  override def name = "stop"

  override def aliases = List("shutdown")

  override def description = "Shut the bot down"

  override def execute(message: Message, args: String) {
    // Wait a little to add the reaction, but give up quickly as shutting down is more important
    Await.ready(message.addReaction("👌").queueFuture(), 300.millis)
    bot.stop()
  }
}

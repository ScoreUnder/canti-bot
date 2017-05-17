package score.discord.generalbot.command

import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.GeneralBot

class StopCommand(bot: GeneralBot, val userId: Long) extends Command.OneUserOnly {
  override def name = "stop"

  override def aliases = List("shutdown")

  override def description = "Shut the bot down."

  override def execute(message: Message, args: String) {
    message.addReaction("👌").queue()
    // Sleep to give time to add the reaction.
    // But shutting down is more important, so don't actually wait for it to finish.
    try Thread.sleep(300)
    catch {
      case _: InterruptedException =>
    }
    bot.stop()
  }
}

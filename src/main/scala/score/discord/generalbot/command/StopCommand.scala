package score.discord.generalbot.command
import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.GeneralBot

class StopCommand(bot: GeneralBot, val userId: Long) extends Command.OneUserOnly {
  override def name = "stop"

  override def aliases = List("shutdown")

  override def description = "Shut the bot down."

  override def execute(message: Message, args: String) {
    bot.stop()
  }
}

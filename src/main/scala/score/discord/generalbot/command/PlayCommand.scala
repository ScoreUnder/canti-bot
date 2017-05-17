package score.discord.generalbot.command

import net.dv8tion.jda.core.entities.{Game, Message}

class PlayCommand(val userId: Long) extends Command.OneUserOnly {
  override def name = "playing"

  override def aliases = Nil

  override def description = "Set the game that this bot is currently playing"

  override def execute(message: Message, args: String) = {
    message.getJDA.getPresence.setGame(args match {
      case "" => null
      case _ => Game of args
    })
    message.addReaction("👌").queue()
  }
}

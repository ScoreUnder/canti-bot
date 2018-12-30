package score.discord.generalbot.command

import net.dv8tion.jda.core.entities.{Game, Message, User}
import score.discord.generalbot.wrappers.jda.ID

class PlayCommand(val userId: ID[User]) extends Command.OneUserOnly {
  override def name = "playing"

  override def aliases = Nil

  override def description = "Set the game that this bot is currently playing"

  override def execute(message: Message, args: String) = {
    message.getJDA.getPresence.setGame(args match {
      case "" => null
      case name => Game playing name
    })
    message.addReaction("👌").queue()
  }

  override def executeForEdit(message: Message, myMessageOption: Option[ID[Message]], args: String): Unit =
    execute(message, args)
}

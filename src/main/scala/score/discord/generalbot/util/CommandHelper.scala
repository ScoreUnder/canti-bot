package score.discord.generalbot.util

import net.dv8tion.jda.core.entities

object CommandHelper {
  def apply(message: entities.Message): Message = new CommandHelper.Message(message)

  class Message(val _me: entities.Message) extends AnyVal {
    def guild = Option(_me.getGuild).toRight("You can only use this command from within a server.")

    def member =
      guild flatMap { (guild) =>
        Option(guild.getMember(_me.getAuthor))
          .toRight("Internal error: Can't find your server membership. This might be a temporary problem.")
      }
  }

}

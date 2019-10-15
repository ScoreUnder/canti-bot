package score.discord.generalbot.util

import net.dv8tion.jda.api.entities.{Guild, Member}
import net.dv8tion.jda.api.{MessageBuilder, entities}

object CommandHelper {
  def apply(message: entities.Message): Message = new CommandHelper.Message(message)

  class Message(val _me: entities.Message) extends AnyVal {
    def guild: Either[String, Guild] = Option(_me.getGuild).toRight("You can only use this command from within a server.")

    def member: Either[String, Member] =
      guild flatMap { (guild) =>
        Option(guild.getMember(_me.getAuthor))
          .toRight("Internal error: Can't find your server membership. This might be a temporary problem.")
      }

    def mentionsToPlaintext(input: String = _me.getContentRaw): String = {
      import net.dv8tion.jda.api.entities.Message.MentionType._
      val builder = new MessageBuilder().append(input)
      guild match {
        case Right(guild) =>
          builder.stripMentions(guild, USER, ROLE, CHANNEL)
        case Left(_) =>
          builder.stripMentions(_me.getJDA, USER, ROLE, CHANNEL)
      }
      builder.getStringBuilder.toString
    }
  }
}

package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.User
import score.discord.generalbot.wrappers.jda.Conversions._

class RichUser(val me: User) extends AnyVal {
  def name = me.getName

  def discriminator = me.getDiscriminator

  def mention = me.getAsMention

  def unambiguousString = s"User(${me.id} /* $name#$discriminator */)"
}

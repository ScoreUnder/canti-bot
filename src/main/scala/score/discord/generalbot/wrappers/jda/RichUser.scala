package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities.User

class RichUser(val me: User) extends AnyVal {
  def id = me.getIdLong

  def name = me.getName

  def discriminator = me.getDiscriminator

  def mention = me.getAsMention

  def unambiguousString = s"User($id /* $name#$discriminator */)"
}

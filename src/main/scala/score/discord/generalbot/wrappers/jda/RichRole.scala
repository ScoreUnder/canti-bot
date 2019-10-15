package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.entities.Role

class RichRole(val role: Role) extends AnyVal {
  def mention = role.getAsMention
}

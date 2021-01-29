package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.Role

class RichRole(val role: Role) extends AnyVal {
  /** The mention string for this role */
  def mention = role.getAsMention
}

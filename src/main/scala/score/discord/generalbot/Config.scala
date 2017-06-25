package score.discord.generalbot

import net.dv8tion.jda.core.entities.User
import score.discord.generalbot.wrappers.jda.ID

class Config(val token: String, val owner: ID[User])

object Config {
  val BOT_OWNER = new ID[User](226521865537978368L)  // TODO: Make this configurable

  def load(config: com.typesafe.config.Config) = {
    new Config(
      token = config.getString("token"),
      owner = new ID[User](config.getLong("owner"))
    )
  }
}

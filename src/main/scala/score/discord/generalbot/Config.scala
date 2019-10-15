package score.discord.generalbot

import net.dv8tion.jda.api.entities.User
import score.discord.generalbot.wrappers.jda.ID

class Config(val token: String, val owner: ID[User])

object Config {
  def load(config: com.typesafe.config.Config) = {
    new Config(
      token = config.getString("token"),
      owner = new ID[User](config.getLong("owner"))
    )
  }
}

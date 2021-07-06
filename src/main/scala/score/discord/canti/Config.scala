package score.discord.canti

import net.dv8tion.jda.api.entities.User
import score.discord.canti.wrappers.jda.ID

class Config(val token: String, val owner: ID[User])

object Config:
  def load(config: com.typesafe.config.Config) =
    Config(
      token = config.getString("token"),
      owner = ID[User](config.getLong("owner"))
    )

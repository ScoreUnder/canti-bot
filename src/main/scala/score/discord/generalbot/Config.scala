package score.discord.generalbot

class Config(val token: String, val owner: Long)

object Config {
  val BOT_OWNER = 226521865537978368L  // TODO: Make this configurable

  def load(config: com.typesafe.config.Config) = {
    new Config(
      token = config.getString("token"),
      owner = config.getLong("owner")
    )
  }
}

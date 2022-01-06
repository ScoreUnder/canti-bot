package score.discord.canti

import net.dv8tion.jda.api.entities.User
import score.discord.canti.wrappers.jda.ID

class Config(
  val token: String,
  val owner: ID[User],
  val hasMessageIntent: Boolean,
  val hasGuildMembersIntent: Boolean
)

object Config:
  def load(config: com.typesafe.config.Config) =
    Config(
      token = config.getString("token").nn,
      owner = ID[User](config.getLong("owner")),
      hasMessageIntent = config.getBoolean("has_message_intent"),
      hasGuildMembersIntent = config.getBoolean("has_guild_members_intent")
    )

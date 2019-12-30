package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Guild, MessageChannel, Role, User, VoiceChannel}

import scala.language.implicitConversions

object IdConversions {
  implicit def toRichMessageChannelId(id: ID[MessageChannel]): RichMessageChannelId = new RichMessageChannelId(id.value)
  implicit def toRichGuildId(id: ID[Guild]): RichGuildId = new RichGuildId(id.value)
  implicit def toRichVoiceChannelId(id: ID[VoiceChannel]): RichVoiceChannelId = new RichVoiceChannelId(id.value)
  implicit def toRichUserId(id: ID[User]): RichUserId = new RichUserId(id.value)
  implicit def toRichRoleId(id: ID[Role]): RichRoleId = new RichRoleId(id.value)
}

class RichMessageChannelId(val me: Long) extends AnyVal {
  def find(implicit jda: JDA): Option[MessageChannel] =
    Option(jda.getTextChannelById(me)).orElse(Option(jda.getPrivateChannelById(me)))
}

class RichGuildId(val me: Long) extends AnyVal {
  def find(implicit jda: JDA): Option[Guild] = Option(jda.getGuildById(me))
}

class RichVoiceChannelId(val me: Long) extends AnyVal {
  def find(implicit jda: JDA): Option[VoiceChannel] = Option(jda.getVoiceChannelById(me))
}

class RichUserId(val me: Long) extends AnyVal {
  def find(implicit jda: JDA): Option[User] = Option(jda.getUserById(me))
}

class RichRoleId(val me: Long) extends AnyVal {
  def find(implicit jda: JDA): Option[Role] = Option(jda.getRoleById(me))
}

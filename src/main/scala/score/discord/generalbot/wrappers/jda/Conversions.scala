package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.entities._
import net.dv8tion.jda.core.requests.RestAction
import net.dv8tion.jda.core.{EmbedBuilder, JDA, MessageBuilder}

import scala.language.implicitConversions

object Conversions {
  implicit final def toRichChannel(channel: Channel): RichChannel = new RichChannel(channel)

  implicit final def toRichMessageChannel(channel: MessageChannel): RichMessageChannel = new RichMessageChannel(channel)

  implicit final def toRichMessage(message: Message): RichMessage = new RichMessage(message)

  implicit final def toRichMember(member: Member): RichMember = new RichMember(member)

  implicit final def toRichJDA(jDA: JDA): RichJDA = new RichJDA(jDA)

  implicit final def toRichGuild(guild: Guild): RichGuild = new RichGuild(guild)

  implicit final def toRichUser(user: User): RichUser = new RichUser(user)

  implicit final def toRichSnowflake[T <: ISnowflake](snowflake: T): RichSnowflake[T] = new RichSnowflake[T](snowflake)

  implicit final def toRichRole(role: Role): RichRole = new RichRole(role)

  implicit final def toRichRestAction[T](restAction: RestAction[T]): RichRestAction[T] = new RichRestAction[T](restAction)

  trait MessageFromX {
    def toMessage: Message
  }

  implicit class MessageFromString(me: String) extends MessageFromX {
    def toMessage = new MessageBuilder().append(me).build
  }

  implicit class MessageFromMessageBuilder(me: MessageBuilder) extends MessageFromX {
    def toMessage = new MessageBuilder().append(me).build
  }

  implicit class MessageFromEmbedBuilder(me: EmbedBuilder) extends MessageFromX {
    def toMessage = new MessageBuilder().setEmbed(me.build).build
  }

  implicit class MessageFromEmbed(me: MessageEmbed) extends MessageFromX {
    def toMessage = new MessageBuilder().setEmbed(me).build
  }

  implicit class MessageFromMessage(me: Message) extends MessageFromX {
    def toMessage = me
  }

}

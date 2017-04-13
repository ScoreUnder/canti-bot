package score.discord.generalbot.wrappers

import net.dv8tion.jda.core.entities.{Channel, Member, MessageChannel}

object Conversions {
  implicit final def toRichChannel(channel: Channel): RichChannel = new RichChannel(channel)

  implicit final def toRichMessageChannel(channel: MessageChannel): RichMessageChannel = new RichMessageChannel(channel)

  implicit final def toRichMember(member: Member): RichMember = new RichMember(member)
}

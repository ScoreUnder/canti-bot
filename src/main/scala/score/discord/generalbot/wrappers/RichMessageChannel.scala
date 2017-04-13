package score.discord.generalbot.wrappers

import net.dv8tion.jda.core.entities.{Message, MessageChannel, MessageEmbed}
import net.dv8tion.jda.core.{EmbedBuilder, MessageBuilder}

class RichMessageChannel(val channel: MessageChannel) extends AnyVal {
  def !(message: String) = channel.sendMessage(message).queue()

  def !(message: Message) = channel.sendMessage(message).queue()

  def !(message: MessageBuilder) = channel.sendMessage(message.build).queue()

  def !(message: MessageEmbed) = channel.sendMessage(message).queue()

  def !(message: EmbedBuilder) = channel.sendMessage(message.build).queue()
}

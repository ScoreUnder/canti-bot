package score.discord.generalbot.jdamocks

import java.util
import java.util.Collections

import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.{Message, MessageChannel, MessageEmbed, User}
import net.dv8tion.jda.core.entities.impl.AbstractMessage
import score.discord.generalbot.SnowflakeOrdering

class FakeMessage(channel: MessageChannel, id: Long, content: String, author: User, embeds: util.List[MessageEmbed])
  extends AbstractMessage(content, "dummy nonce", false)
  with SnowflakeOrdering
{
  override def unsupported(): Unit = ???

  override def getChannel: MessageChannel = channel

  override def getAuthor: User = author

  override def getEmbeds: util.List[MessageEmbed] = embeds

  override def getAttachments: util.List[Message.Attachment] = Collections.emptyList()

  override def getJDA: JDA = channel.getJDA

  override def getIdLong: Long = id
}

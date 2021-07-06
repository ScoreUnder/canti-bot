package score.discord.canti.jdamocks

import java.util
import java.util.Collections

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Message, MessageActivity, MessageChannel, MessageEmbed, User}
import net.dv8tion.jda.internal.entities.AbstractMessage
import score.discord.canti.SnowflakeOrdering

class FakeMessage(channel: MessageChannel, id: Long, content: String, author: User, embeds: util.List[MessageEmbed])
  extends AbstractMessage(content, "dummy nonce", false)
  with SnowflakeOrdering:

  override def getJumpUrl: String = s"https://dummy.jump.url/$id"

  override def unsupported(): Unit = ???

  override def getChannel: MessageChannel = channel

  override def getAuthor: User = author

  override def getEmbeds: util.List[MessageEmbed] = embeds

  override def getAttachments: util.List[Message.Attachment] = Collections.emptyList()

  override def getJDA: JDA = channel.getJDA

  override def getIdLong: Long = id

  override def getActivity: MessageActivity = ???

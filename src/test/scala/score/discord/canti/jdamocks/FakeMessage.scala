package score.discord.canti.jdamocks

import java.util
import java.util.Collections

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Message, MessageActivity, MessageEmbed, User}
import net.dv8tion.jda.internal.entities.AbstractMessage
import score.discord.canti.SnowflakeOrdering

import scala.jdk.CollectionConverters.*
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.sticker.Sticker
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.entities.sticker.StickerItem
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData

class FakeMessage(
  channel: MessageChannel,
  id: Long,
  content: String,
  author: User | Null,
  embeds: util.List[MessageEmbed]
) extends AbstractMessage(content, "dummy nonce", false)
    with SnowflakeOrdering:
  override def getJumpUrl: String = s"https://dummy.jump.url/$id"

  override def unsupported(): Unit = ???

  override def getChannel: MessageChannelUnion =
    channel.asInstanceOf[MessageChannelUnion]

  override def getAuthor: User =
    author.nn // should not be called on a message that isn't "received"

  override def getEmbeds: util.List[MessageEmbed] = embeds

  override def getAttachments: util.List[Message.Attachment] = Nil.asJava

  override def getJDA: JDA = channel.getJDA.nn

  override def getIdLong: Long = id

  override def getActivity: MessageActivity = ???

  override def getStickers: util.List[StickerItem] = Nil.asJava

  override def getApplicationIdLong(): Long = ???

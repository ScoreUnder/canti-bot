package score.discord.generalbot.jdamocks

import java.util
import java.util.Collections

import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities._
import net.dv8tion.jda.core.managers.{ChannelManager, ChannelManagerUpdatable}
import net.dv8tion.jda.core.requests.RestAction
import net.dv8tion.jda.core.requests.restaction._
import score.discord.generalbot.wrappers.jda.ID

import scala.util.Try

class FakeTextChannel(guild: FakeGuild, id: Long, name: String) extends TextChannel {
  private var cachedMessages = Map.empty[Long, Message]
  private var lastMessage: Long = 0L

  def addMessage(content: String, author: User, embeds: util.List[MessageEmbed] = Collections.emptyList[MessageEmbed]): FakeMessage = {
    val message = new FakeMessage(channel = this, id = guild.fakeJda.nextId, content = content, author = author, embeds = embeds)
    cachedMessages += message.getIdLong -> message
    lastMessage = message.getIdLong
    message
  }

  override def getTopic: String = ???

  override def isNSFW: Boolean = ???

  override def getWebhooks: RestAction[util.List[Webhook]] = ???

  override def createWebhook(name: String): WebhookAction = ???

  override def deleteMessages(messages: util.Collection[Message]): RestAction[Void] = ???

  override def deleteMessagesByIds(messageIds: util.Collection[String]): RestAction[Void] = ???

  override def getMessageById(messageId: Long): RestAction[Message] = FakeMessageAction(cachedMessages(messageId))

  override def deleteWebhookById(id: String): AuditableRestAction[Void] = ???

  override def clearReactionsById(messageId: String): RestAction[Void] = ???

  override def removeReactionById(messageId: String, unicode: String, user: User): RestAction[Void] = ???

  override def getMessageById(messageId: String): RestAction[Message] = getMessageById(ID.fromString(messageId).value)

  override def canTalk: Boolean = ???

  override def canTalk(member: Member): Boolean = ???

  override def compareTo(o: TextChannel): Int = ???

  override def getGuild: Guild = guild

  override def getParent: Category = ???

  override def getMembers: util.List[Member] = ???

  override def getPosition: Int = ???

  override def getPositionRaw: Int = ???

  override def getPermissionOverride(member: Member): PermissionOverride = ???

  override def getPermissionOverride(role: Role): PermissionOverride = ???

  override def getPermissionOverrides: util.List[PermissionOverride] = ???

  override def getMemberPermissionOverrides: util.List[PermissionOverride] = ???

  override def sendMessage(msg: Message): MessageAction =
    FakeMessageAction(
      addMessage(content = msg.getContentRaw, author = Try(msg.getAuthor).toOption.orNull, embeds = msg.getEmbeds)
    )

  override def getRolePermissionOverrides: util.List[PermissionOverride] = ???

  override def createCopy(guild: Guild): ChannelAction = ???

  override def getManager: ChannelManager = ???

  override def getManagerUpdatable: ChannelManagerUpdatable = ???

  override def delete(): AuditableRestAction[Void] = ???

  override def createPermissionOverride(member: Member): PermissionOverrideAction = ???

  override def createPermissionOverride(role: Role): PermissionOverrideAction = ???

  override def putPermissionOverride(member: Member): PermissionOverrideAction = ???

  override def putPermissionOverride(role: Role): PermissionOverrideAction = ???

  override def createInvite(): InviteAction = ???

  override def getInvites: RestAction[util.List[Invite]] = ???

  override def getLatestMessageIdLong: Long = lastMessage

  override def hasLatestMessage: Boolean = ???

  override def getName: String = name

  override def getType: ChannelType = ChannelType.TEXT

  override def getJDA: JDA = guild.getJDA

  override def getAsMention: String = s"<#$id>"

  override def getIdLong: Long = id
}

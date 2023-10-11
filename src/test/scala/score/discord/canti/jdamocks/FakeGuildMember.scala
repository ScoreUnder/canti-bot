package score.discord.canti.jdamocks

import java.awt.Color
import java.time.OffsetDateTime
import java.util

import net.dv8tion.jda.api.{JDA, OnlineStatus, Permission}
import net.dv8tion.jda.api.entities.*

import scala.jdk.CollectionConverters.*
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion

class FakeGuildMember(guild: Guild, user: User) extends Member:
  val myPerms = Vector.empty[Permission]
  val myChanPerms =
    import net.dv8tion.jda.api.Permission.*
    Vector(MESSAGE_SEND, VIEW_CHANNEL, MESSAGE_HISTORY)

  override def getUser: User = user

  override def getGuild: Guild = guild

  override def getJDA: JDA = guild.getJDA.nn

  override def getTimeJoined: OffsetDateTime = ???

  override def getVoiceState: GuildVoiceState = ???

  override def getActivities: util.List[Activity] = ???

  override def getOnlineStatus: OnlineStatus = ???

  override def getNickname: String = ???

  override def getEffectiveName: String = ???

  override def getRoles: util.List[Role] = ???

  override def getColor: Color = ???

  override def getColorRaw: Int = ???

  override def getPermissions(channel: GuildChannel): util.EnumSet[Permission] =
    util.EnumSet.copyOf(myChanPerms.asJava).nn

  override def canInteract(member: Member): Boolean = ???

  override def canInteract(role: Role): Boolean = ???

  override def isOwner: Boolean = ???

  override def getDefaultChannel: DefaultGuildChannelUnion = ???

  override def getAsMention: String = ???

  override def getPermissions: util.EnumSet[Permission] = util.EnumSet.copyOf(myPerms.asJava).nn

  override def hasPermission(permissions: Array[? <: Permission]): Boolean =
    getPermissions.containsAll(util.Arrays.asList(permissions*))

  override def hasPermission(permissions: util.Collection[Permission]): Boolean =
    getPermissions.containsAll(permissions)

  override def hasPermission(channel: GuildChannel, permissions: Array[? <: Permission]): Boolean =
    getPermissions(channel).containsAll(util.Arrays.asList(permissions*))

  override def hasPermission(
    channel: GuildChannel,
    permissions: util.Collection[Permission]
  ): Boolean = getPermissions(channel).containsAll(permissions)

  override def getTimeBoosted: OffsetDateTime = ???

  override def getOnlineStatus(`type`: ClientType): OnlineStatus = ???

  override def getPermissionsExplicit: util.EnumSet[Permission] = ???

  override def getPermissionsExplicit(channel: GuildChannel): util.EnumSet[Permission] = ???

  override def getIdLong: Long = user.getIdLong

  override def getActiveClients: util.EnumSet[ClientType] = ???

  override def hasTimeJoined: Boolean = ???

  override def isPending: Boolean = ???

  override def getAvatarId(): String | Null = ???

  override def getTimeOutEnd(): OffsetDateTime | Null = ???

  override def canInteract(emoji: RichCustomEmoji | Null): Boolean = ???

  override def getDefaultAvatarId(): String | Null = ???

  override def isBoosting(): Boolean = ???

  override def canSync(channel: IPermissionContainer | Null): Boolean = ???

  override def canSync(
    targetChannel: IPermissionContainer | Null,
    syncSource: IPermissionContainer | Null
  ): Boolean = ???

  override def getFlagsRaw(): Int = ???

end FakeGuildMember

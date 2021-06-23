package score.discord.canti.jdamocks

import java.awt.Color
import java.time.OffsetDateTime
import java.util

import net.dv8tion.jda.api.Permission._
import net.dv8tion.jda.api.{JDA, OnlineStatus, Permission}
import net.dv8tion.jda.api.entities._

import scala.jdk.CollectionConverters._

class FakeGuildMember(guild: Guild, user: User) extends Member {
  val myPerms = Vector.empty[Permission]
  val myChanPerms = Vector(MESSAGE_READ, MESSAGE_WRITE, VIEW_CHANNEL)

  override def getUser: User = user

  override def getGuild: Guild = guild

  override def getJDA: JDA = guild.getJDA

  override def getTimeJoined: OffsetDateTime = ???

  override def getVoiceState: GuildVoiceState = ???

  override def getActivities: util.List[Activity] = ???

  override def getOnlineStatus: OnlineStatus = ???

  override def getNickname: String = ???

  override def getEffectiveName: String = ???

  override def getRoles: util.List[Role] = ???

  override def getColor: Color = ???

  override def getColorRaw: Int = ???

  override def getPermissions(channel: GuildChannel): util.EnumSet[Permission] = util.EnumSet.copyOf(myChanPerms.asJava)

  override def canInteract(member: Member): Boolean = ???

  override def canInteract(role: Role): Boolean = ???

  override def canInteract(emote: Emote): Boolean = ???

  override def isOwner: Boolean = ???

  override def getDefaultChannel: TextChannel = ???

  override def getAsMention: String = ???

  override def getPermissions: util.EnumSet[Permission] = util.EnumSet.copyOf(myPerms.asJava)

  override def hasPermission(permissions: Permission*): Boolean = getPermissions.containsAll(permissions.asJava)

  override def hasPermission(permissions: util.Collection[Permission]): Boolean = getPermissions.containsAll(permissions)

  override def hasPermission(channel: GuildChannel, permissions: Permission*): Boolean = getPermissions(channel).containsAll(permissions.asJava)

  override def hasPermission(channel: GuildChannel, permissions: util.Collection[Permission]): Boolean = getPermissions(channel).containsAll(permissions)

  override def getTimeBoosted: OffsetDateTime = ???

  override def getOnlineStatus(`type`: ClientType): OnlineStatus = ???

  override def getPermissionsExplicit: util.EnumSet[Permission] = ???

  override def getPermissionsExplicit(channel: GuildChannel): util.EnumSet[Permission] = ???

  override def getIdLong: Long = user.getIdLong

  override def getActiveClients: util.EnumSet[ClientType] = ???

  override def hasTimeJoined: Boolean = ???

  override def isPending: Boolean = ???

  override def canSync(targetChannel: GuildChannel, syncSource: GuildChannel): Boolean = ???

  override def canSync(channel: GuildChannel): Boolean = ???
}

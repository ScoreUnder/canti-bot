package score.discord.generalbot.jdamocks

import java.awt.Color
import java.time.OffsetDateTime
import java.util

import net.dv8tion.jda.core.Permission._
import net.dv8tion.jda.core.{JDA, OnlineStatus, Permission}
import net.dv8tion.jda.core.entities._

import scala.collection.JavaConverters._

class FakeGuildMember(guild: Guild, user: User) extends Member {
  var myPerms = Vector.empty[Permission]
  var myChanPerms = Vector(MESSAGE_READ, MESSAGE_WRITE, VIEW_CHANNEL)

  override def getUser: User = user

  override def getGuild: Guild = guild

  override def getJDA: JDA = guild.getJDA

  override def getJoinDate: OffsetDateTime = ???

  override def getVoiceState: GuildVoiceState = ???

  override def getGame: Game = ???

  override def getOnlineStatus: OnlineStatus = ???

  override def getNickname: String = ???

  override def getEffectiveName: String = ???

  override def getRoles: util.List[Role] = ???

  override def getColor: Color = ???

  override def getColorRaw: Int = ???

  override def getPermissions(channel: Channel): util.List[Permission] = myChanPerms.asJava

  override def canInteract(member: Member): Boolean = ???

  override def canInteract(role: Role): Boolean = ???

  override def canInteract(emote: Emote): Boolean = ???

  override def isOwner: Boolean = ???

  override def getDefaultChannel: TextChannel = ???

  override def getAsMention: String = ???

  override def getPermissions: util.List[Permission] = myPerms.asJava

  override def hasPermission(permissions: Permission*): Boolean = getPermissions.containsAll(permissions.asJava)

  override def hasPermission(permissions: util.Collection[Permission]): Boolean = getPermissions.containsAll(permissions)

  override def hasPermission(channel: Channel, permissions: Permission*): Boolean = getPermissions(channel).containsAll(permissions.asJava)

  override def hasPermission(channel: Channel, permissions: util.Collection[Permission]): Boolean = getPermissions(channel).containsAll(permissions)
}

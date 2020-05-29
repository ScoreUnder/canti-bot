package score.discord.generalbot.jdamocks

import java.util

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Guild, PrivateChannel, User}
import net.dv8tion.jda.api.requests.RestAction

class FakeUser(name: String, id: Long) extends User {
  override def getName: String = name

  override def getDiscriminator: String = "1234"

  override def getAvatarId: String = "123456789"

  override def getDefaultAvatarId: String = ???

  override def hasPrivateChannel: Boolean = ???

  override def openPrivateChannel(): RestAction[PrivateChannel] = ???

  override def getMutualGuilds: util.List[Guild] = ???

  override def isBot: Boolean = ???

  override def getJDA: JDA = ???

  override def getIdLong: Long = id

  override def getAsMention: String = ???

  override def isFake: Boolean = ???

  override def getAsTag: String = ???

  override def getFlags: util.EnumSet[User.UserFlag] = ???

  override def getFlagsRaw: Int = ???
}

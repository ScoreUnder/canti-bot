package score.discord.generalbot.jdamocks

import java.util

import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.{Guild, PrivateChannel, User}
import net.dv8tion.jda.core.requests.RestAction

class FakeUser(name: String, id: Long) extends User {
  override def getName: String = name

  override def getDiscriminator: String = "1234"

  override def getAvatarId: String = ???

  override def getAvatarUrl: String = "http://example.com/fake-avatar.jpg"

  override def getDefaultAvatarId: String = ???

  override def getDefaultAvatarUrl: String = ???

  override def getEffectiveAvatarUrl: String = ???

  override def hasPrivateChannel: Boolean = ???

  override def openPrivateChannel(): RestAction[PrivateChannel] = ???

  override def getMutualGuilds: util.List[Guild] = ???

  override def isBot: Boolean = ???

  override def getJDA: JDA = ???

  override def getIdLong: Long = id

  override def getAsMention: String = ???

  override def isFake: Boolean = ???
}

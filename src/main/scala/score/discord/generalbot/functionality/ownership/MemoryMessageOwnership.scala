package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.{Message, User}
import score.discord.generalbot.collections.{Cache, NullCacheBackend}
import score.discord.generalbot.functionality.ownership.MemoryMessageOwnership.MyCache
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global

object MemoryMessageOwnership {
  type MyCache = Cache[ID[Message], ID[Message], Option[ID[User]]]
}

class MemoryMessageOwnership(cacheFactory: (MyCache#Backend) => MyCache) extends MessageOwnership {
  private[this] val cache = cacheFactory(new NullCacheBackend.Unmapped)

  override def apply(jda: JDA, messageId: ID[Message]) = async {
    for {
      userId <- await(cache(messageId))
      user <- jda.findUser(userId)
    } yield user
  }

  override def update(message: Message, user: User) {
    cache(message.id) = Some(user.id)
  }

  override def remove(message: ID[Message]) {
    cache(message) = None
  }
}

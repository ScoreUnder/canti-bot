package score.discord.generalbot.functionality.ownership

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.generalbot.collections.{Cache, NullCacheBackend}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.IdConversions._
import score.discord.generalbot.wrappers.jda.ID

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MemoryMessageOwnership(cacheBase: Cache[ID[Message], Option[ID[User]]]) extends MessageOwnership {
  private[this] val cache = NullCacheBackend of cacheBase

  override def apply(messageId: ID[Message])(implicit jda: JDA): Future[Option[User]] = async {
    for {
      userId <- await(cache(messageId))
      user <- userId.find
    } yield user
  }

  override def update(message: Message, user: User) {
    cache(message.id) = Some(user.id)
  }

  override def remove(message: ID[Message]) {
    // Invalidating instead of setting to None, because it's unlikely to be
    // referenced in the future (since the message was most likely deleted).
    // Invalidating allows us to free just a tiny bit more memory.
    cache.invalidate(message)
  }
}

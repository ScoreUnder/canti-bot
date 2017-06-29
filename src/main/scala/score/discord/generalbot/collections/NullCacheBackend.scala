package score.discord.generalbot.collections

import scala.concurrent.Future

object NullCacheBackend {

  class Unmapped[T] extends Cache.Backend[T, T, Option[Nothing]] {
    override def keyToId(key: T): T = key

    override def get(key: T): Future[Option[Nothing]] = Future.successful(None)
  }

}

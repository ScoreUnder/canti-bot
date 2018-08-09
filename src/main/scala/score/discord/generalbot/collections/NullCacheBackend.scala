package score.discord.generalbot.collections

import scala.concurrent.Future
import scala.language.existentials

object NullCacheBackend {
  def of[K, V](cache: Cache[K, Option[V]]) = new cache.Backend[K] {
    override def keyToId(key: K): K = key

    override def missing(key: K): Future[Option[Nothing]] = Future.successful(None)
  }
}

package score.discord.generalbot.collections

import scala.concurrent.Future

trait Cache[K, I, V] {
  type Backend = Cache.Backend[K, I, V]
  protected val backend: Backend
  def apply(extra: K): Future[V]
  def update(key: K, value: V): Unit = updateById(backend.keyToId(key), value)
  def updateById(key: I, value: V): Unit
  def invalidate(key: K): Unit = invalidateById(backend.keyToId(key))
  def invalidateById(key: I): Unit
}

object Cache {
  trait Backend[-K, +I, +V] {
    def keyToId(key: K): I
    def get(key: K): Future[V]
  }
}

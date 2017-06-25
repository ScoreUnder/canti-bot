package score.discord.generalbot.collections

import java.util
import java.util.Map.Entry

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object LruCache {
  def empty[K, I, V](maxCapacity: Int, initialCapacity: Option[Int] = None, loadFactor: Float = 0.75f)
                              (backend: Cache.Backend[K, I, V]) =
    new LruCache[K, I, V](maxCapacity, initialCapacity, loadFactor)(backend)
}

class LruCache[K, I, V](val maxCapacity: Int, initialCapacity: Option[Int] = None, loadFactor: Float = 0.75f)
                                 (protected val backend: Cache.Backend[K, I, V]) extends Cache[K, I, V] {
  // Backing LinkedHashMap which discards at a certain capacity
  // "true" for accessOrder makes it reorder nodes on access to function as a LRU cache
  private[this] val backing = new util.LinkedHashMap[I, V](initialCapacity.getOrElse(64 min maxCapacity), loadFactor, true) {
    override def removeEldestEntry(entry: Entry[I, V]) = size > maxCapacity
  }

  // Cache requests which already exist (so that they are not repeatedly re-requested before the first completes)
  private[this] val ongoingRequests = mutable.HashMap.empty[I, Future[V]]

  def apply(key: K): Future[V] =
    this.synchronized {
      backing get key match {
        case null =>
          val keyId = backend.keyToId(key)
          ongoingRequests.getOrElseUpdate(keyId, {
            val freshValue = backend.get(key)
            freshValue.onComplete(_ => onRequestComplete(keyId, freshValue))
            freshValue
          })
        case existing => Future.successful(existing)
      }
    }

  def invalidateById(key: I) {
    this.synchronized {
      ongoingRequests.remove(key)
      backing.remove(key)
    }
  }

  def updateById(key: I, value: V) {
    this.synchronized {
      ongoingRequests.remove(key)
      backing.put(key, value)
    }
  }

  // Delete ongoing request and put into cache when complete
  private def onRequestComplete(key: I, future: Future[V]) {
    require(future.isCompleted)
    this.synchronized {
      ongoingRequests.remove(key) match {
        case Some(futureInProgress) if futureInProgress == future =>
          // Only update cache if our request was not invalidated in the meantime
          future.foreach(backing.put(key, _))
        case _ =>
      }
    }
  }
}

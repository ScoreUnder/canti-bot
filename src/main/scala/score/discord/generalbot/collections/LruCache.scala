package score.discord.generalbot.collections

import java.util
import java.util.Map.Entry

object LruCache {
  def empty[K, V](maxCapacity: Int, initialCapacity: Option[Int] = None, loadFactor: Float = 0.75f) =
    new LruCache[K, V](maxCapacity, initialCapacity, loadFactor)
}

class LruCache[K, V](val maxCapacity: Int, initialCapacity: Option[Int] = None, loadFactor: Float = 0.75f)
  extends Cache[K, V] {
  // Backing LinkedHashMap which discards at a certain capacity
  // "true" for accessOrder makes it reorder nodes on access to function as a LRU cache
  private[this] val backing = new util.LinkedHashMap[K, V](initialCapacity.getOrElse(64 min maxCapacity), loadFactor, true) {
    override def removeEldestEntry(entry: Entry[K, V]) = size > maxCapacity
  }

  def apply(key: K): Option[V] = Option(backing.get(key))

  def invalidate(key: K) {
    backing.remove(key)
  }

  def update(key: K, value: V) {
    backing.put(key, value)
  }
}

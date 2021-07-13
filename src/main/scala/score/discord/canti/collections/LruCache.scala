package score.discord.canti.collections

import score.discord.canti.wrappers.NullWrappers.*

import java.util
import java.util.Map.Entry

object LruCache:
  def empty[K, V](
    maxCapacity: Int,
    initialCapacity: Option[Int] = None,
    loadFactor: Float = 0.75f
  ) =
    LruCache[K, V](maxCapacity, initialCapacity, loadFactor)

class LruCache[K, V](
  val maxCapacity: Int,
  initialCapacity: Option[Int] = None,
  loadFactor: Float = 0.75f
) extends CacheLayer[K, Option[V]]:
  private type OV = Option[V]
  // Backing LinkedHashMap which discards at a certain capacity
  // "true" for accessOrder makes it reorder nodes on access to function as a LRU cache
  private val cache =
    new util.LinkedHashMap[K, OV](initialCapacity.getOrElse(64 min maxCapacity), loadFactor, true):
      override def removeEldestEntry(entry: Entry[K, OV]) = size > maxCapacity

  override def get(key: K): Option[OV] =
    cache.get(key).?

  override def update(key: K, value: OV): Unit =
    cache.put(key, value)

  override def invalidate(key: K): Unit =
    cache.remove(key)

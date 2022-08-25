package score.discord.canti.collections

import score.discord.canti.wrappers.NullWrappers.*

import java.lang.ref.SoftReference
import java.util
import java.util.Map.Entry

object LruCache:
  def empty[K, V](
    maxCapacity: Int,
    initialCapacity: Option[Int] = None,
    loadFactor: Float = 0.75f
  ): LruCache[K, V] =
    LruCache[K, V](maxCapacity, initialCapacity, loadFactor)

class LruCache[K, V](
  val maxCapacity: Int,
  initialCapacity: Option[Int] = None,
  loadFactor: Float = 0.75f
) extends CacheLayer[K, Option[V]]:
  private type OV = Option[V]
  // Backing LinkedHashMap which discards at a certain capacity
  // "true" for accessOrder makes it reorder nodes on access to function as a LRU cache
  private var cache = SoftReference[util.LinkedHashMap[K, OV]](null)

  private def getCache =
    val existingCache = cache.get()
    if existingCache != null then existingCache
    else
      val newCache =
        new util.LinkedHashMap[K, OV](
          initialCapacity.getOrElse(64 min maxCapacity),
          loadFactor,
          true
        ):
          override def removeEldestEntry(entry: Entry[K, OV]): Boolean = size > maxCapacity
      cache = SoftReference(newCache)
      newCache

  override def get(key: K): Option[OV] =
    cache.get.?.flatMap(_.get(key).?)

  override def update(key: K, value: OV): Unit =
    getCache.put(key, value)

  override def invalidate(key: K): Unit =
    cache.get.?.foreach(_.remove(key))

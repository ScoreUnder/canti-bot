package score.discord.generalbot.collections

trait CacheLayer[K, V] {
  def get(key: K): Option[V]

  def update(key: K, value: V): Unit

  def invalidate(key: K): Unit
}

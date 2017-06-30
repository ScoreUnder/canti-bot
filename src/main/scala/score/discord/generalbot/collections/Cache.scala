package score.discord.generalbot.collections

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Cache[K, V] {
  def apply(key: K): Option[V]

  def update(key: K, value: V): Unit

  def invalidate(key: K): Unit

  abstract class Backend[-T] {
    // Cache requests which already exist (so that they are not repeatedly re-requested before the first completes)
    private[this] val ongoingRequests = mutable.HashMap.empty[K, Future[V]]

    def keyToId(key: T): K

    def missing(key: T): Future[V]

    def apply(key: T): Future[V] = {
      ongoingRequests.synchronized {
        Cache.this (keyToId(key)) match {
          case None =>
            val keyId = keyToId(key)
            ongoingRequests.getOrElseUpdate(keyId, {
              val freshValue = missing(key)
              freshValue.onComplete(_ => onRequestComplete(keyId, freshValue))
              freshValue
            })
          case Some(existing) => Future.successful(existing)
        }
      }
    }

    def invalidate(key: T): Unit = invalidateById(keyToId(key))

    def invalidateById(key: K) {
      ongoingRequests.synchronized {
        ongoingRequests.remove(key)
        Cache.this.invalidate(key)
      }
    }

    def update(key: T, value: V): Unit = updateById(keyToId(key), value)

    def updateById(key: K, value: V) {
      ongoingRequests.synchronized {
        ongoingRequests.remove(key)
        Cache.this (key) = value
      }
    }

    // Delete ongoing request and put into cache when complete
    private def onRequestComplete(key: K, future: Future[V]) {
      require(future.isCompleted)
      ongoingRequests.synchronized {
        ongoingRequests.remove(key) match {
          case Some(futureInProgress) if futureInProgress == future =>
            // Only update cache if our request was not invalidated in the meantime
            future.foreach(Cache.this (key) = _)
          case _ =>
        }
      }
    }
  }

}

package score.discord.canti.collections

import org.slf4j.LoggerFactory
import score.discord.canti.collections.CacheCoordinator.logger

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.chaining.*
import scala.util.{Failure, Success}

class CacheCoordinator[K, V](cache: CacheLayer[K, Option[V]], backend: AsyncMap[K, V])
  extends AsyncMap[K, V]:
  // Cache requests which already exist (so that they are not repeatedly re-requested before the first completes)
  private[this] val ongoingRequests = mutable.HashMap.empty[K, Future[Option[V]]]

  def get(key: K): Future[Option[V]] = ongoingRequests.synchronized {
    cache.get(key) match
      case None => ongoingRequests.getOrElseUpdate(key,
        backend.get(key).tap { freshValueFuture =>
          freshValueFuture.onComplete(_ => onRequestComplete(key, freshValueFuture))
        })
      case Some(existing) => Future.successful(existing)
  }

  def invalidate(key: K): Unit =
    ongoingRequests.synchronized {
      ongoingRequests.remove(key)
      cache.invalidate(key)
    }

  def update(key: K, value: V): Future[Int] =
    ongoingRequests.synchronized {
      ongoingRequests.remove(key)
      cache(key) = Some(value)
    }
    backend.update(key, value)

  override def remove(key: K): Future[Int] =
    ongoingRequests.synchronized {
      ongoingRequests.remove(key)
      cache(key) = None
    }
    backend.remove(key)

  override def items: Future[Seq[(K, V)]] = backend.items // Not cached

  // Delete ongoing request and put into cache when complete
  private def onRequestComplete(key: K, future: Future[Option[V]]): Unit =
    future.value match
      case Some(Success(value)) => ongoingRequests.synchronized {
        ongoingRequests.remove(key) match
          // Only update cache if our request was not invalidated in the meantime
          case Some(`future`) => cache(key) = value
          case _ =>
      }
      case Some(Failure(exception)) => logger.error("Cache request completed with failure", exception)
      case None => throw IllegalStateException("onRequestComplete() called for incomplete future")

object CacheCoordinator:
  private[CacheCoordinator] val logger = LoggerFactory.getLogger(getClass)

  extension[K, V](me: AsyncMap[K, V])
    def withCache(cache: CacheLayer[K, Option[V]]): CacheCoordinator[K, V] =
      CacheCoordinator[K, V](cache, me)

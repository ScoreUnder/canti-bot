package score.discord.generalbot.collections
import scala.concurrent.Future

class NullCacheBackend[K, V] extends AsyncMap[K, V] {
  private val futureNone = Future.successful(None)

  override def get(key: K): Future[Option[Nothing]] = futureNone

  override def update(key: K, value: V): Future[Int] = Future.never

  override def remove(key: K): Future[Int] = Future.never

  override val items: Future[Seq[Nothing]] = Future.successful(Nil)
}

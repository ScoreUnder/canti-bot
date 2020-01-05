package score.discord.generalbot.wrappers

import score.discord.generalbot.collections.AsyncMap

import scala.concurrent.Future
import scala.language.implicitConversions

object AsyncMapExtras {
  implicit def toRichAsyncMap[K, V](me: AsyncMap[K, V]): RichAsyncMap[K, V] = new RichAsyncMap[K, V](me)

  class RichAsyncMap[K, V](val me: AsyncMap[K, V]) extends AnyVal {
    def unmapKeys[K2](kf: K2 => K): AsyncMap[K2, V] = new AsyncMap[K2, V] {
      override def get(key: K2): Future[Option[V]] = me.get(kf(key))

      override def update(key: K2, value: V): Future[Int] = me(kf(key)) = value

      override def remove(key: K2): Future[Int] = me.remove(kf(key))

      override def items: Future[Seq[(K2, V)]] = throw new UnsupportedOperationException()
    }
  }
}

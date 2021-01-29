package score.discord.canti.jdamocks

import java.util
import java.util.concurrent.locks.ReentrantReadWriteLock

import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.api.utils.{ClosableIterator, LockIterator}

import scala.jdk.CollectionConverters._

class ScalaCacheView[T](cache: Iterable[T], getName: T => String) extends CacheView[T] {
  val lock = new ReentrantReadWriteLock

  override def asList(): util.List[T] = new util.ArrayList[T](cache.asJavaCollection)

  override def asSet(): util.NavigableSet[T] = new util.TreeSet[T](cache.asJavaCollection)

  override def size(): Long = cache.size

  override def isEmpty: Boolean = cache.isEmpty

  override def getElementsByName(name: String, ignoreCase: Boolean): util.List[T] = {
    val eq = if (ignoreCase) name.equals _ else name.equalsIgnoreCase _
    new util.ArrayList[T](cache.filter(el => eq(getName(el))).asJavaCollection)
  }

  override def stream(): util.stream.Stream[T] = cache.asJavaCollection.stream()

  override def parallelStream(): util.stream.Stream[T] = cache.asJavaCollection.parallelStream()

  override def iterator(): util.Iterator[T] = cache.asJava.iterator()

  override def lockedIterator(): ClosableIterator[T] = {
    val readLock = lock.readLock
    readLock.lock()
    try new LockIterator[T](cache.iterator.asJava, readLock) catch {
      case t: Throwable =>
        readLock.unlock()
        throw t
    }
  }
}

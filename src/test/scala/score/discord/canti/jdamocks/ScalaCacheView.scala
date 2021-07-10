package score.discord.canti.jdamocks

import java.util
import java.util.concurrent.locks.ReentrantReadWriteLock

import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.api.utils.{ClosableIterator, LockIterator}

import scala.jdk.CollectionConverters.*

class ScalaCacheView[T](cache: Iterable[T], getName: T => String) extends CacheView[T]:
  val lock = ReentrantReadWriteLock()

  override def asList(): util.List[T] = util.ArrayList[T](cache.asJavaCollection)

  override def asSet(): util.NavigableSet[T] = util.TreeSet[T](cache.asJavaCollection)

  override def size(): Long = cache.size

  export cache.isEmpty

  override def getElementsByName(name: String, ignoreCase: Boolean): util.List[T] =
    val eq = if ignoreCase then name.equals else name.equalsIgnoreCase
    util.ArrayList[T](cache.filter(el => eq(getName(el))).asJavaCollection)

  override def stream(): util.stream.Stream[T] = cache.asJavaCollection.stream().nn

  override def parallelStream(): util.stream.Stream[T] = cache.asJavaCollection.parallelStream().nn

  override def iterator(): util.Iterator[T] = cache.iterator.asJava

  override def lockedIterator(): ClosableIterator[T] =
    val readLock = lock.readLock.nn
    readLock.lock()
    try LockIterator[T](cache.iterator.asJava, readLock) catch
      case t: Throwable =>
        readLock.unlock()
        throw t

package score.discord.generalbot.jdamocks

import java.util

import net.dv8tion.jda.core.utils.cache.CacheView
import scala.collection.JavaConverters._

class ScalaCacheView[T](cache: Iterable[T], getName: T => String) extends CacheView[T] {
  override def asList(): util.List[T] = new util.ArrayList[T](cache.asJavaCollection)

  override def asSet(): util.Set[T] = new util.HashSet[T](cache.asJavaCollection)

  override def size(): Long = cache.size

  override def isEmpty: Boolean = cache.isEmpty

  override def getElementsByName(name: String, ignoreCase: Boolean): util.List[T] = {
    val eq = if (ignoreCase) name.equals _ else name.equalsIgnoreCase _
    new util.ArrayList[T](cache.filter(el => eq(getName(el))).asJavaCollection)
  }

  override def stream(): util.stream.Stream[T] = cache.asJavaCollection.stream()

  override def parallelStream(): util.stream.Stream[T] = cache.asJavaCollection.parallelStream()

  override def iterator(): util.Iterator[T] = cache.asJava.iterator()
}

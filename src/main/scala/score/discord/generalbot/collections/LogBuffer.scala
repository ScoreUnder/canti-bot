package score.discord.generalbot.collections

import scala.collection.AbstractIterable

class LogBuffer[T](capacity: Int) extends AbstractIterable[T] {
  private[this] val buffer = new Array[Any](capacity)
  private var readPos, writePos = 0
  private[this] var isEmpty_ = true

  override def size: Int =
    if (isEmpty_) 0
    else readPos - writePos match {
      case x if x > 0 => x
      case x => x + buffer.length
    }

  override def isEmpty: Boolean = isEmpty_

  def ::=(elem: T) = this synchronized {
    val nextWrite = writePos match {
      case 0 => buffer.length - 1
      case x => x - 1
    }
    buffer(nextWrite) = elem
    if (readPos == writePos && !isEmpty)
      readPos = nextWrite
    writePos = nextWrite
    isEmpty_ = false
  }

  override def iterator: Iterator[T] = new Iterator[T] {
    private[this] var myPos = writePos

    override def hasNext: Boolean = myPos != readPos && !LogBuffer.this.isEmpty

    override def next(): T = {
      val pos = myPos
      val result = buffer(pos).asInstanceOf[T]
      myPos = pos + 1 match {
        case x if x == buffer.length => 0
        case x => x
      }
      result
    }
  }
}

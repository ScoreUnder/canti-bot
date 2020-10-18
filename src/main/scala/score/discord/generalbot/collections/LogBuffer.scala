package score.discord.generalbot.collections

import scala.collection.mutable

class LogBuffer[T](capacity: Int) extends mutable.IndexedSeq[T] {
  private[this] val buffer = new Array[Any](capacity)
  private var readPos, writePos = 0
  private[this] var isEmpty_ = true

  override def length: Int =
    if (isEmpty_) 0
    else readPos - writePos match {
      case x if x > 0 => x
      case x => x + buffer.length
    }

  override def isEmpty: Boolean = isEmpty_

  def ::=(elem: T): Unit = this synchronized {
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

  override def apply(idx: Int) =
    buffer(idxToBufferIdx(idx)).asInstanceOf[T]

  override def update(idx: Int, elem: T): Unit =
    buffer(idxToBufferIdx(idx)) = elem

  private def idxToBufferIdx(idx: Int) =
    (idx + writePos) % buffer.length

  def findAndUpdate(condition: T => Boolean)(replace: T => T): this.type = {
    this synchronized {
      val index = this.indexWhere(condition)
      if (index != -1)
        this(index) = replace(this(index))
    }
    this
  }

  override def iterator: Iterator[T] = new Iterator[T] {
    private[this] var myPos = writePos
    private[this] var iterated = LogBuffer.this.isEmpty

    override def hasNext: Boolean = myPos != readPos || !iterated

    override def next(): T = {
      val pos = myPos
      val result = buffer(pos).asInstanceOf[T]
      myPos = pos + 1 match {
        case x if x == buffer.length => 0
        case x => x
      }
      iterated = true
      result
    }
  }
}
